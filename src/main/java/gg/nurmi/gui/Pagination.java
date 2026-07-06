package gg.nurmi.gui;

import java.util.List;

/** Simple slice-by-page helper shared by every paginated menu (shop items, homes, warps, guild members). */
public final class Pagination<T> {

    private final List<T> items;
    private final int pageSize;

    public Pagination(List<T> items, int pageSize) {
        this.items = items;
        this.pageSize = pageSize;
    }

    public int pageCount() {
        return Math.max(1, (int) Math.ceil(items.size() / (double) pageSize));
    }

    public List<T> page(int page) {
        int from = Math.max(0, page * pageSize);
        if (from >= items.size()) {
            return List.of();
        }
        int to = Math.min(items.size(), from + pageSize);
        return items.subList(from, to);
    }
}
