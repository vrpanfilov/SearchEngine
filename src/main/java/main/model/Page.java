package main.model;

import lombok.Data;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "page")
@Data
public class Page implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "FK_page_site"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site site;
    @Column(columnDefinition = "text", nullable = false)
    private String path;
    @Column(nullable = false)
    private int code;
    @Column(columnDefinition = "mediumtext", nullable = false)
    private String content;

    @Transient
    private String title;

    @Override
    public int hashCode() {
        return path != null && site != null ? path.hashCode() + site.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        Page p = (Page) obj;
        return site == null ||
                getClass() == obj.getClass() && path.equals(p.path) && site == p.site;
    }

    @Override
    public String toString() {
        return "id: " + id + ", siteId: " + site.getId() + ", path: " + path;
    }
}
