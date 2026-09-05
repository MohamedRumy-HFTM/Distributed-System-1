package org.acme.control;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Huellt eine Seite von Datensaetzen zusammen mit Metadaten ein.
 *
 * Ein nacktes JSON-Array kann keine Zusatzinformationen transportieren. Erst
 * durch totalCount weiss ein Frontend, wie viele Seiten es insgesamt gibt, und
 * kann eine Blaetter-Navigation aufbauen.
 */
public class PagedResult<T> {

    @Schema(description = "Die Datensaetze dieser Seite.")
    private List<T> data;

    @Schema(description = "Gesamtzahl der Treffer, unabhaengig von der Seitengroesse.", examples = { "42" })
    private long totalCount;

    @Schema(description = "Nullbasierter Index der gelieferten Seite.", examples = { "0" })
    private int page;

    @Schema(description = "Angeforderte Seitengroesse.", examples = { "10" })
    private int size;

    @Schema(description = "Anzahl verfuegbarer Seiten.", examples = { "5" })
    private int pageCount;

    public PagedResult() {
    }

    public PagedResult(List<T> data, long totalCount, int page, int size, int pageCount) {
        this.data = data;
        this.totalCount = totalCount;
        this.page = page;
        this.size = size;
        this.pageCount = pageCount;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }
}
