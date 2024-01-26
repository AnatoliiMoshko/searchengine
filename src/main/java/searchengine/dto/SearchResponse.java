package searchengine.dto;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SearchResponse {
    private boolean result;
    private String error;
    private Integer count;
    private List<SearchData> data;

    public SearchResponse(boolean result, Integer count, List<SearchData> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }
}