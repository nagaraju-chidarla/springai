package com.ai.springai.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private static final double SIMILARITY_THRESHOLD = 0.75;

    private final VectorStore vectorStore;


    public RagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> search(String question) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();
        List<Document> documents =
                vectorStore.similaritySearch(searchRequest);

        // Useful while developing
        System.out.println("======================================");
        System.out.println("Question: " + question);
        System.out.println("Documents found: " + documents.size());

        for (Document document : documents) {
            System.out.println("--------------------------------------");
            System.out.println("Score: " + document.getScore());
            System.out.println("Source: " + document.getMetadata().get("source"));
            System.out.println("Content:");
            System.out.println(document.getText());
        }

        System.out.println("======================================");

        return documents;
    }

    public String buildContext(List<Document> documents) {

        return documents.stream()
                .map(document -> {

                    String source = String.valueOf(
                            document.getMetadata().get("source")
                    );

                    String section = String.valueOf(
                            document.getMetadata().get("section")
                    );

                    return """
                            Source: %s
                            Section: %s

                            Policy:
                            %s
                            """.formatted(
                            source,
                            section,
                            document.getText()
                    );
                })
                .reduce((doc1, doc2) -> doc1 + "\n\n" + doc2)
                .orElse("");
    }
}
