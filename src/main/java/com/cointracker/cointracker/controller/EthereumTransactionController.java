package com.cointracker.cointracker.controller;
import com.cointracker.cointracker.service.EthereumTransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ethereum")
public class EthereumTransactionController {

    @Autowired
    private EthereumTransactionService ethereumTransactionService;

    @GetMapping("/transactions")
    public ResponseEntity<String> fetchTransactions(@RequestParam String walletAddress) {
        CompletableFuture.runAsync(() -> {
            try {
                ethereumTransactionService.fetchAndSaveTransactions(walletAddress);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return ResponseEntity.ok("Transaction fetch process started. CSV will be generated.");
    }
}
