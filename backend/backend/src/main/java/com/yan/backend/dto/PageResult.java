package com.yan.backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果。
 *
 * <p>不直接把 Spring Data 的 Page 返回给前端，原因有两个：
 * 一是 Page 序列化出来的字段名（content/number/size/totalElements）既啰嗦又不好用；
 * 二是 Spring Data 4 起对 Page 的 JSON 序列化做了调整并会打警告，不如自己收口。
 *
 * <p>pageNum 对外是**从 1 开始**的，而 Spring Data 内部从 0 开始，转换在 of() 里做。
 */
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /** 直接由 Spring Data 的 Page 转换（实体与返回值类型相同时用） */
    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize());
    }

    /** 需要把实体映射成 VO 时用这个重载 */
    public static <E, T> PageResult<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResult<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize());
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
