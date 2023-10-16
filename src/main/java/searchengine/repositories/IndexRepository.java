package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SearchIndex;
import searchengine.model.SiteEntity;
import java.util.ArrayList;

@Repository
public interface IndexRepository extends JpaRepository<SearchIndex, Integer> {
    ArrayList<SearchIndex> findByLemmaID_LemmaAndPageID_SiteID(String lemma, SiteEntity site);
    ArrayList<SearchIndex> findByPageIDAndLemmaID_Lemma(PageEntity page, String lemma);
}