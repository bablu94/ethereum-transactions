# Ethereum Transaction Tracker

This is a Java-based Spring Boot application that fetches Ethereum transaction data from the Etherscan API and saves it in a CSV file.

## Features
- Fetch Ethereum transactions, ERC-20, and ERC-721/ERC-1155 token transfers.
- Retry logic with exponential backoff to handle API rate limits.
- Saves transaction data into a CSV file for analysis.

## Prerequisites
- Java 8 or higher
- Maven for dependency management
- Etherscan API Key

## Setup

1. Clone the repository:
    ```bash
    git clone https://github.com/bablu94/ethereum-transactions.git
    ```

2. Navigate to the project directory:
    ```bash
    cd ethereum-transactions
    ```

3. Configure your Etherscan API key:
   - Open `src/main/resources/application.properties`.
   - Add your API key and base URL:
     ```properties
     etherscan.apiKey=your-etherscan-api-key
     etherscan.baseUrl=https://api.etherscan.io/api
     ```

## Build and Run

### Using Maven
1. **Build the project**:
    ```bash
    mvn clean install
    ```

2. **Run the application**:
    ```bash
    mvn spring-boot:run
    ```

   This will start the application and listen on `http://localhost:8080`.
   - The transactions will be fetched and saved in `transactions.csv`.

## Usage

To fetch transactions for a specific Ethereum wallet, use the following API endpoint:
