package main.apiResponses;

import lombok.Data;

@Data
public class PageData {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
