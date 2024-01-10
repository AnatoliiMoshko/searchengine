package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Data
@Table(name = "lemmas")
public class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn (name = "site_id", referencedColumnName = "id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SiteEntity siteID;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    private String lemma;

    @Column(nullable = false)
    private int frequency;
}