package searchengine.services;

import searchengine.dto.SearchResponse;

public interface SearchingService {
    SearchResponse getSearchResults(String query, String site, Integer offset, Integer limit);
}