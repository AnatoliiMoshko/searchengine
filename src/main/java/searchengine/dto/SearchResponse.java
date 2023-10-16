package searchengine.dto;

import lombok.*;
import searchengine.dto.SearchData;

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

    public SearchResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }
}