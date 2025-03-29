package com.cointracker.cointracker.service;

import com.opencsv.CSVWriter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class EthereumTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(EthereumTransactionService.class);

    @Value("${etherscan.apiKey}")
    private String etherscanApiKey;

    @Value("${etherscan.baseUrl}")
    private String etherscanBaseUrl;

    private final OkHttpClient client = new OkHttpClient();

    private static final int MAX_OFFSET = 10000;
    private static final int MAX_RETRIES = 5;
    private static final int RETRY_BASE_DELAY = 1000; // In milliseconds

    public void fetchAndSaveTransactions(String walletAddress) {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<List<String[]>>> futures = new ArrayList<>();

        futures.add(executor.submit(() -> fetchTransactions(walletAddress, "txlist"))); // Normal Transfers
        futures.add(executor.submit(() -> fetchTransactions(walletAddress, "tokentx"))); // ERC-20 Transfers
        futures.add(executor.submit(() -> fetchTransactions(walletAddress, "tokennfttx"))); // ERC-721 & ERC-1155 Transfers

        List<String[]> allTransactions = new ArrayList<>();
        for (Future<List<String[]>> future : futures) {
            try {
                allTransactions.addAll(future.get());
            } catch (Exception e) {
                logger.error("Error fetching transaction data", e);
            }
        }

        executor.shutdown();
        try {
            saveToCSV(allTransactions);
        } catch (IOException e) {
            logger.error("Error saving transactions to CSV", e);
        }
    }

    private List<String[]> fetchTransactions(String walletAddress, String action) throws IOException, InterruptedException {
        List<String[]> transactions = new ArrayList<>();
        int page = 1, offset = MAX_OFFSET;
        boolean moreData = true;

        while (moreData) {
            String url = etherscanBaseUrl + "?module=account&action=" + action +
                    "&address=" + walletAddress + "&startblock=0&endblock=99999999&page=" + page +
                    "&offset=" + offset + "&sort=asc&apikey=" + etherscanApiKey;

            Request request = new Request.Builder().url(url).build();
            Response response = null;
            int attempt = 0;

            // Retry with exponential backoff
            while (attempt < MAX_RETRIES) {
                try {
                    response = client.newCall(request).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        String responseData = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseData);

                        if (!jsonObject.has("result") || jsonObject.isNull("result")) {
                            if (responseData.contains("Result window is too large")) {
                                logger.warn("Result window too large for {}. Reducing offset size.", action);
                                offset = offset / 2; // Dynamically reduce offset size for large results
                                continue;
                            }
                            logger.warn("API returned null result for {}: {}", action, responseData);
                            break;
                        }

                        JSONArray resultArray = jsonObject.getJSONArray("result");
                        if (resultArray.isEmpty()) {
                            moreData = false;
                        } else {
                            transactions.addAll(parseTransactions(resultArray, action));
                            page++;
                            Thread.sleep(200);
                        }
                        break; // Exit retry loop if successful
                    } else {
                        logger.error("Failed to fetch transactions for {}: HTTP {}", action, response.code());
                        break;
                    }
                } catch (IOException e) {
                    logger.error("Attempt {} failed: {}", attempt + 1, e.getMessage());
                    if (attempt < MAX_RETRIES - 1) {
                        Thread.sleep(RETRY_BASE_DELAY * (1 << attempt)); // Exponential backoff
                    }
                }
                attempt++;
            }
            if (attempt == MAX_RETRIES) {
                logger.error("Max retries reached for fetching data from Etherscan.");
                break;
            }
        }
        return transactions;
    }

    private List<String[]> parseTransactions(JSONArray transactions, String action) {
        List<String[]> data = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < transactions.length(); i++) {
            JSONObject tx = transactions.getJSONObject(i);

            String transactionType;
            switch (action) {
                case "txlist":
                    transactionType = "ETH Transfer";
                    break;
                case "tokentx":
                    transactionType = "ERC-20";
                    break;
                case "tokennfttx":
                    transactionType = tx.has("tokenID") ? "ERC-721 / ERC-1155" : "Contract Interaction";
                    break;
                default:
                    transactionType = "Unknown";
            }

            String gasFee = tx.has("gasPrice") && tx.has("gasUsed")
                    ? new BigDecimal(tx.getString("gasPrice"))
                    .multiply(new BigDecimal(tx.getString("gasUsed")))
                    .divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP)
                    .toPlainString()
                    : "0";

            String timestamp = dateFormat.format(new Date(tx.optLong("timeStamp") * 1000));

            data.add(new String[]{
                    tx.optString("hash"),                        // Transaction Hash
                    timestamp,                                   // Date & Time
                    tx.optString("from"),                        // From Address
                    tx.optString("to"),                          // To Address
                    transactionType,                             // Transaction Type
                    tx.optString("contractAddress"),             // Asset Contract Address
                    tx.optString("tokenSymbol", "ETH"),          // Asset Symbol / Name
                    tx.optString("tokenID", ""),                 // Token ID
                    tx.optString("value"),                       // Value / Amount
                    gasFee                                       // Gas Fee (ETH)
            });
        }
        return data;
    }

    private void saveToCSV(List<String[]> transactions) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter("transactions.csv"))) {
            writer.writeNext(new String[]{
                    "Transaction Hash", "Date & Time", "From Address", "To Address",
                    "Transaction Type", "Asset Contract Address", "Asset Symbol / Name",
                    "Token ID", "Value / Amount", "Gas Fee (ETH)"
            });
            writer.writeAll(transactions);
            logger.info("CSV file successfully saved with {} transactions.", transactions.size());
        } catch (IOException e) {
            logger.error("Error writing CSV file: {}", e.getMessage());
            throw e;
        }
    }
}
