// File: StockTradingApp.java
// Compile: javac StockTradingApp.java
// Run: java StockTradingApp

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StockTradingApp {

    // ======== DOMAIN MODELS ========

    static class Stock {
        private final String ticker;
        private final String name;
        private double price;

        public Stock(String ticker, String name, double price) {
            this.ticker = ticker.toUpperCase(Locale.ROOT);
            this.name = name;
            this.price = price;
        }

        public String getTicker() { return ticker; }
        public String getName() { return name; }
        public double getPrice() { return price; }

        public void updatePrice(Random rng) {
            double pct = (rng.nextDouble() * 0.08) - 0.04; // ±4%
            price = Math.max(1.0, round2(price * (1.0 + pct)));
        }

        @Override
        public String toString() {
            return String.format("%-6s %-18s ₹%,.2f", ticker, name, price);
        }
    }

    static class Market {
        private final Map<String, Stock> stocks = new HashMap<>();
        private final Random rng = new Random();

        public void addStock(Stock s) { stocks.put(s.getTicker(), s); }
        public Stock get(String ticker) { return stocks.get(ticker.toUpperCase(Locale.ROOT)); }
        public Collection<Stock> list() { return stocks.values(); }

        public void tickAll() {
            for (Stock s : stocks.values()) s.updatePrice(rng);
        }
    }

    enum TradeType { BUY, SELL }

    static class Transaction {
        final LocalDateTime time;
        final TradeType type;
        final String ticker;
        final int shares;
        final double price;
        final double total;

        Transaction(TradeType type, String ticker, int shares, double price) {
            this(LocalDateTime.now(), type, ticker, shares, price,
                    (type == TradeType.BUY ? 1 : -1) * round2(price * shares));
        }

        Transaction(LocalDateTime time, TradeType type, String ticker, int shares, double price, double total) {
            this.time = time;
            this.type = type;
            this.ticker = ticker;
            this.shares = shares;
            this.price = price;
            this.total = total;
        }

        @Override
        public String toString() {
            return String.format("%s  %-4s %-6s %4d @ ₹%,.2f  total: %s₹%,.2f",
                    time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    type, ticker, shares, price,
                    (type == TradeType.BUY ? "" : "-"), Math.abs(total));
        }

        String toCsv() {
            return String.join(",",
                    time.toString(), type.toString(), ticker,
                    Integer.toString(shares), Double.toString(price), Double.toString(total));
        }

        static Transaction fromCsv(String line) {
            String[] p = line.split(",", -1);
            return new Transaction(
                    LocalDateTime.parse(p[0]),
                    TradeType.valueOf(p[1]),
                    p[2],
                    Integer.parseInt(p[3]),
                    Double.parseDouble(p[4]),
                    Double.parseDouble(p[5])
            );
        }
    }

    static class Position {
        final String ticker;
        int shares;
        double avgCost;

        Position(String ticker) { this.ticker = ticker; }

        void applyBuy(int qty, double price) {
            double costBefore = avgCost * shares;
            double costAdd = price * qty;
            shares += qty;
            avgCost = round4((costBefore + costAdd) / shares);
        }

        void applySell(int qty) {
            shares -= qty;
            if (shares <= 0) {
                shares = 0;
                avgCost = 0;
            }
        }
    }

    static class Portfolio {
        private final Map<String, Position> map = new HashMap<>();

        public Position get(String ticker) { return map.get(ticker); }
        public Collection<Position> positions() { return map.values(); }

        public void buy(String ticker, int qty, double price) {
            Position p = map.computeIfAbsent(ticker, Position::new);
            p.applyBuy(qty, price);
        }

        public boolean canSell(String ticker, int qty) {
            Position p = map.get(ticker);
            return p != null && p.shares >= qty;
        }

        public void sell(String ticker, int qty) {
            Position p = map.get(ticker);
            if (p != null) {
                p.applySell(qty);
                if (p.shares == 0) map.remove(ticker);
            }
        }

        public double marketValue(Market mkt) {
            double sum = 0;
            for (Position p : map.values()) {
                Stock s = mkt.get(p.ticker);
                if (s != null) sum += s.getPrice() * p.shares;
            }
            return round2(sum);
        }

        public void saveTo(File file) throws IOException {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("ticker,shares,avgCost");
                for (Position p : map.values()) {
                    pw.printf("%s,%d,%.6f%n", p.ticker, p.shares, p.avgCost);
                }
            }
        }

        public static Portfolio loadFrom(File file) throws IOException {
            Portfolio pf = new Portfolio();
            if (!file.exists()) return pf;
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line = br.readLine(); // header
                while ((line = br.readLine()) != null) {
                    String[] a = line.split(",", -1);
                    Position p = new Position(a[0]);
                    p.shares = Integer.parseInt(a[1]);
                    p.avgCost = Double.parseDouble(a[2]);
                    pf.map.put(p.ticker, p);
                }
            }
            return pf;
        }
    }

    static class User {
        final String name;
        double cash;
        final Portfolio portfolio = new Portfolio();
        final List<Transaction> history = new ArrayList<>();

        User(String name, double initialCash) {
            this.name = name;
            this.cash = initialCash;
        }

        boolean buy(Market mkt, String ticker, int qty) {
            Stock s = mkt.get(ticker);
            if (s == null || qty <= 0) return false;
            double cost = round2(s.getPrice() * qty);
            if (cash < cost) return false;
            cash = round2(cash - cost);
            portfolio.buy(s.getTicker(), qty, s.getPrice());
            history.add(new Transaction(TradeType.BUY, s.getTicker(), qty, s.getPrice()));
            return true;
        }

        boolean sell(Market mkt, String ticker, int qty) {
            Stock s = mkt.get(ticker);
            if (s == null || qty <= 0 || !portfolio.canSell(s.getTicker(), qty)) return false;
            double proceeds = round2(s.getPrice() * qty);
            cash = round2(cash + proceeds);
            portfolio.sell(s.getTicker(), qty);
            history.add(new Transaction(TradeType.SELL, s.getTicker(), qty, s.getPrice()));
            return true;
        }

        double netWorth(Market mkt) {
            return round2(cash + portfolio.marketValue(mkt));
        }

        void saveAll(String prefix) throws IOException {
            new File(".").mkdirs();
            try (PrintWriter pw = new PrintWriter(new FileWriter(prefix + "_cash.txt"))) {
                pw.println(cash);
            }
            portfolio.saveTo(new File(prefix + "_portfolio.csv"));
            try (PrintWriter pw = new PrintWriter(new FileWriter(prefix + "_transactions.csv"))) {
                pw.println("time,type,ticker,shares,price,total");
                for (Transaction t : history) {
                    pw.println(t.toCsv());
                }
            }
        }

        void loadAll(String prefix) throws IOException {
            File cashF = new File(prefix + "_cash.txt");
            if (cashF.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(cashF))) {
                    String line = br.readLine();
                    if (line != null) cash = Double.parseDouble(line.trim());
                }
            }
            Portfolio loaded = Portfolio.loadFrom(new File(prefix + "_portfolio.csv"));
            this.portfolio.map.clear();
            this.portfolio.map.putAll(loaded.map);
            File txF = new File(prefix + "_transactions.csv");
            this.history.clear();
            if (txF.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(txF))) {
                    String line = br.readLine(); // header
                    while ((line = br.readLine()) != null) {
                        this.history.add(Transaction.fromCsv(line));
                    }
                }
            }
        }
    }

    // ======== APP (Console) ========

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Market market = seedMarket();
        User user = new User("Trader", 100_000);

        System.out.println("Welcome to the Stock Trading Simulator (Java OOP)");
        boolean running = true;

        while (running) {
            System.out.println("\n==== MENU ====");
            System.out.println("1) Update market (tick prices)");
            System.out.println("2) List market data");
            System.out.println("3) Buy stock");
            System.out.println("4) Sell stock");
            System.out.println("5) View portfolio & P/L");
            System.out.println("6) View transactions");
            System.out.println("7) Save (./data_*)");
            System.out.println("8) Load (./data_*)");
            System.out.println("9) Net worth");
            System.out.println("0) Exit");
            System.out.print("Select: ");

            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1" -> { market.tickAll(); System.out.println("Market updated."); }
                case "2" -> { market.tickAll(); listMarket(market); }
                case "3" -> {
                    System.out.print("Enter Ticker: ");
                    String t = sc.nextLine().trim().toUpperCase(Locale.ROOT);
                    System.out.print("Enter Quantity: ");
                    int q = safeInt(sc.nextLine());
                    if (user.buy(market, t, q)) System.out.println("BUY executed.");
                    else System.out.println("BUY failed.");
                }
                case "4" -> {
                    System.out.print("Enter Ticker: ");
                    String t = sc.nextLine().trim().toUpperCase(Locale.ROOT);
                    System.out.print("Enter Quantity: ");
                    int q = safeInt(sc.nextLine());
                    if (user.sell(market, t, q)) System.out.println("SELL executed.");
                    else System.out.println("SELL failed.");
                }
                case "5" -> showPortfolio(user, market);
                case "6" -> showHistory(user);
                case "7" -> {
                    try { user.saveAll("data"); System.out.println("Saved to data_* files."); }
                    catch (Exception e) { System.out.println("Save failed: " + e.getMessage()); }
                }
                case "8" -> {
                    try { user.loadAll("data"); System.out.println("Loaded from data_* files."); }
                    catch (Exception e) { System.out.println("Load failed: " + e.getMessage()); }
                }
                case "9" -> {
                    System.out.printf("Cash: ₹%,.2f | Portfolio: ₹%,.2f | Net Worth: ₹%,.2f%n",
                            user.cash, user.portfolio.marketValue(market), user.netWorth(market));
                }
                case "0" -> { running = false; System.out.println("Goodbye!"); }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    // ======== HELPERS ========

    private static Market seedMarket() {
        Market m = new Market();
        m.addStock(new Stock("TCS", "TCS Ltd", 3930));
        m.addStock(new Stock("INFY", "Infosys", 1650));
        m.addStock(new Stock("RELI", "Reliance Ind.", 2935));
        m.addStock(new Stock("HDFB", "HDFC Bank", 1560));
        m.addStock(new Stock("ITC", "ITC Ltd", 470));
        m.addStock(new Stock("WIPR", "Wipro", 475));
        m.addStock(new Stock("SBIN", "State Bank", 845));
        return m;
    }

    private static void listMarket(Market market) {
        System.out.println("\nTICKER NAME               PRICE");
        System.out.println("----------------------------------------");
        market.list().stream()
                .sorted(Comparator.comparing(Stock::getTicker))
                .forEach(System.out::println);
    }

    private static void showPortfolio(User user, Market mkt) {
        System.out.println("\nYour Portfolio");
        System.out.println("TICKER  SHARES  AVG COST   PRICE     MKT VALUE   UPL");
        System.out.println("-------------------------------------------------------------");
        double mv = 0, upl = 0, cost = 0;
        for (Position p : user.portfolio.positions().stream()
                .sorted(Comparator.comparing(pos -> pos.ticker)).toList()) {
            Stock s = mkt.get(p.ticker);
            double price = s != null ? s.getPrice() : 0;
            double value = round2(price * p.shares);
            double pUpl = round2((price - p.avgCost) * p.shares);
            mv += value;
            upl += pUpl;
            cost += p.avgCost * p.shares;
            System.out.printf("%-6s  %6d  ₹%,8.2f  ₹%,7.2f  ₹%,9.2f  ₹%,8.2f%n",
                    p.ticker, p.shares, p.avgCost, price, value, pUpl);
        }
        System.out.println("-------------------------------------------------------------");
        System.out.printf("Cash: ₹%,.2f | Cost: ₹%,.2f | Mkt Value: ₹%,.2f | UPL: ₹%,.2f | Net Worth: ₹%,.2f%n",
                user.cash, cost, mv, upl, round2(user.cash + mv));
    }

    private static void showHistory(User user) {
        System.out.println("\nTransactions");
        if (user.history.isEmpty()) {
            System.out.println("(none)");
            return;
        }
        for (Transaction t : user.history) {
            System.out.println(t);
        }
    }

    private static int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}