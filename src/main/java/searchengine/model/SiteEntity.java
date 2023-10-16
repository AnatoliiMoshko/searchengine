package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "sites")
public class SiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "status_time", nullable = false, columnDefinition = "datetime")
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    private String url;

    @Column(updatable = false, columnDefinition = "varchar(255)")
    private String name;
}