package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.OnDelete;

@Entity
@Data
@Table(name = "indexes")
public class SearchIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", referencedColumnName = "id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PageEntity pageID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", referencedColumnName = "id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private LemmaEntity lemmaID;

    @Column(name = "search_rank", nullable = false)
    private float rank;
}