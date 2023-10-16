package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "indexes")
public class SearchIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", referencedColumnName = "id")
    private PageEntity pageID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", referencedColumnName = "id")
    private LemmaEntity lemmaID;

    @Column(name = "search_rank", nullable = false)
    private float rank;
}