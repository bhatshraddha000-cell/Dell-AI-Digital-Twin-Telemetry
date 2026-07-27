package com.dell.twin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class BM25Retriever {

    // ---------- BM25 Parameters ----------
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private List<String> documents;           // Each document is a text summary
    private List<Map<String, Integer>> termFreqs; // Term frequencies per document
    private Map<String, Integer> docFreq;     // Document frequency (how many docs contain term)
    private double avgDocLength;
    private int totalDocs;

    public BM25Retriever() {
        this.documents = new ArrayList<>();
        this.termFreqs = new ArrayList<>();
        this.docFreq = new HashMap<>();
        this.avgDocLength = 0;
        this.totalDocs = 0;
    }

    // ---------- Tokenization (simple) ----------
    private List<String> tokenize(String text) {
        // Split by non-alphanumeric characters, convert to lowercase
        String[] tokens = text.toLowerCase().split("[^a-zA-Z0-9]+");
        List<String> result = new ArrayList<>();
        for (String t : tokens) {
            if (!t.isEmpty() && t.length() > 1) { // ignore single characters
                result.add(t);
            }
        }
        return result;
    }

    // ---------- Add a single document (text summary) ----------
    public void addDocument(String docText) {
        List<String> tokens = tokenize(docText);
        documents.add(docText);

        // Count term frequencies in this document
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            tf.put(token, tf.getOrDefault(token, 0) + 1);
        }
        termFreqs.add(tf);

        // Update document frequency (global)
        Set<String> uniqueTerms = tf.keySet();
        for (String term : uniqueTerms) {
            docFreq.put(term, docFreq.getOrDefault(term, 0) + 1);
        }

        totalDocs++;
        avgDocLength = (avgDocLength * (totalDocs - 1) + tokens.size()) / totalDocs;
    }

    public void buildFromCSV(String csvFilePath) throws IOException {
    BufferedReader br = new BufferedReader(new FileReader(csvFilePath));
    String line;
    boolean isHeader = true;

    while ((line = br.readLine()) != null) {
        if (isHeader) {
            isHeader = false;
            continue;
        }

        String[] cols = line.split(",");
        if (cols.length < 7) continue;

        // Remove quotes from each token
        java.util.function.Function<String, String> clean = s ->
            s.trim().replaceAll("^\"|\"$", "").trim();

        // ---- FIXED: Column alignment variables exactly mapped to csv schema offsets ----
        String timestamp = clean.apply(cols[0]);
        double cpuLoad = Double.parseDouble(clean.apply(cols[1]));
        double temperature = Double.parseDouble(clean.apply(cols[2])); // matches column 3
        double fanRpm = Double.parseDouble(clean.apply(cols[3]));      // matches column 4
        double batteryPct = Double.parseDouble(clean.apply(cols[4]));  // matches column 5
        double wifiLatency = Double.parseDouble(clean.apply(cols[5])); // matches column 6
        double diskHealth = Double.parseDouble(clean.apply(cols[6]));  // matches column 7
        
        String summary = String.format(
            "At %s, CPU usage was %.1f%%, temperature was %.1f°C, fan ran at %.0f RPM, " +
            "battery was at %.1f%%, WiFi latency was %.2f ms, and disk health was %.1f%%.",
            timestamp, cpuLoad, temperature, fanRpm, batteryPct, wifiLatency, diskHealth
        );

        addDocument(summary);
    }
    br.close();
    System.out.println("BM25 Index built with " + totalDocs + " documents.");
}

    // ---------- BM25 Score Calculation ----------
    private double bm25Score(Map<String, Integer> termFreq, String term, double avgDocLen, int docLen) {
        if (!docFreq.containsKey(term)) return 0.0;

        int tf = termFreq.getOrDefault(term, 0);
        int df = docFreq.get(term);
        double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

        double numerator = tf * (K1 + 1);
        double denominator = tf + K1 * (1 - B + B * (docLen / avgDocLen));

        return idf * (numerator / denominator);
    }

    // ---------- Retrieve top K documents for a query ----------
    public List<String> retrieve(String query, int topK) {
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return new ArrayList<>();

        // Calculate score for each document
        List<double[]> scores = new ArrayList<>(); // [docIndex, score]
        for (int i = 0; i < totalDocs; i++) {
            Map<String, Integer> tf = termFreqs.get(i);
            int docLen = tf.values().stream().mapToInt(Integer::intValue).sum();
            double score = 0.0;
            for (String term : queryTokens) {
                score += bm25Score(tf, term, avgDocLength, docLen);
            }
            scores.add(new double[]{i, score});
        }

        // Sort by score descending
        scores.sort((a, b) -> Double.compare(b[1], a[1]));

        // Collect top K documents
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scores.size()); i++) {
            int idx = (int) scores.get(i)[0];
            results.add(documents.get(idx));
        }
        return results;
    }

    // ---------- Test / Main method to verify ----------
    public static void main(String[] args) throws IOException {
        System.out.println("Building BM25 index from CSV...");

        BM25Retriever retriever = new BM25Retriever();
        // Update the path if your CSV is named differently
        retriever.buildFromCSV("telemetry_final.csv");

        // Test queries
        String[] testQueries = {
            "high temperature",
            "battery draining fast",
            "cpu usage",
            "fan running high"
        };

        for (String q : testQueries) {
            System.out.println("\nQuery: \"" + q + "\"");
            List<String> results = retriever.retrieve(q, 3);
            int rank = 1;
            for (String doc : results) {
                System.out.println("  " + rank + ". " + doc.substring(0, Math.min(80, doc.length())) + "...");
                rank++;
            }
        }
    }
}