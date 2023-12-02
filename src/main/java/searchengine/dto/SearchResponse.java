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
}