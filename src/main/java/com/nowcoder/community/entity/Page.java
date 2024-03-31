package com.nowcoder.community.entity;

//封装分页相关信息
public class Page {

    //当前页码
    private int current = 1;

    //显示上限
    private int limit = 10;

    //数据总数（计算总页数）
    private int rows;

    //查询路径（复用分页链接）
    private String path;

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        if(current >= 1 )   //注意判断数据合法性
            this.current = current;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        if(limit >= 1 && limit <= 100)
            this.limit = limit;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        if(rows >= 0)
            this.rows = rows;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getOffset(){
        //获取当前页的起始行：current * limit - limit
        return (current - 1) * limit;
    }

    public int getTotal(){
        //获取总页数
        if(rows % limit == 0)
            return rows / limit;
        else
            return rows / limit + 1;
    }

    // 根据当前页算出导航栏起始页和结束页
    public int getFrom(){
        int from = current - 2;
        if(from < 1)
            return 1;
        else
            return from;
    }

    public int getTo(){
        int to = current + 2;
        int total = getTotal();
        if(to > total)
            return total;
        else
            return to;
    }
}
