package com.nowcoder.community;

import com.nowcoder.community.dao.DiscussPostMapper;
import com.nowcoder.community.dao.elasticsearch.DiscussPostRepository;
import com.nowcoder.community.entity.DiscussPost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class ElasticSearchTests {
    @Autowired
    private DiscussPostMapper discussMapper;  // 从mysql中调数据往es存

    @Autowired
    private DiscussPostRepository discussRepository;

    @Autowired
    private ElasticsearchTemplate elasticTemplate;  // 以防有些时候Repository处理不了

    @Test
    public void insert() {  // 单条数据插入
        discussRepository.save(discussMapper.selectDiscussPostById(241));
        discussRepository.save(discussMapper.selectDiscussPostById(242));
        discussRepository.save(discussMapper.selectDiscussPostById(243));
        // 结果查看：postman GET  localhost:9200/discusspost/_search
    }

    @Test
    public void insertList() {  // 多条数据插入
        discussRepository.saveAll(discussMapper.selectDiscussPosts(101, 0, 100));  // 肯定条数不足limit，这里是配合mapper方法进行分页
        discussRepository.saveAll(discussMapper.selectDiscussPosts(102, 0, 100));
        // 结果查看：postman GET  localhost:9200/discusspost/_search
    }

    @Test
    public void update() {  // 修改
        DiscussPost post = discussMapper.selectDiscussPostById(241);
        post.setContent("我爱中国");
        discussRepository.save(post);
        // 结果查看：postman GET  localhost:9200/discusspost/_doc/241
    }

    @Test
    public void delete() {  // 删除一条
        discussRepository.deleteById(241);
        // 结果查看：postman GET  localhost:9200/discusspost/_doc/241
    }

    @Test
    public void deleteall(){  // rm -rf /*
        discussRepository.deleteAll();
        // 结果查看：postman GET  localhost:9200/discusspost/_search
    }

    @Test
    public void searchByRepository(){  // 搜索(Repository)
        // es 7 已经不支持了
    }

    @Test
    public void searchByTemplate() {  // 搜索(Template)  --更完善
        // 查询标准构建，匹配字段"content"和"title"中包含"互联网寒冬"关键字的数据
        // 在这里使用matches，而不是contains，contains必须包含完整的关键字，而matches不需要，如果要匹配更多字段使用or或者and
        Criteria criteria = new Criteria("title").matches("互联网寒冬").or(new Criteria("content").matches("互联网寒冬"));

        // 高亮前端格式：<em></em>
        List<HighlightField> highlightFieldList = new ArrayList<>();
        HighlightField highlightField = new HighlightField("title", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
        highlightFieldList.add(highlightField);
        highlightField = new HighlightField("content", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
        highlightFieldList.add(highlightField);
        Highlight highlight = new Highlight(highlightFieldList);
        HighlightQuery highlightQuery = new HighlightQuery(highlight, DiscussPost.class);

        // 构建细化查询
        CriteriaQueryBuilder builder = new CriteriaQueryBuilder(criteria)   // 指定关键词
                .withSort(Sort.by(Sort.Direction.DESC, "type"))  // 置顶
                .withSort(Sort.by(Sort.Direction.DESC, "score"))  // 热度
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))  // 时间
                .withHighlightQuery(highlightQuery)  // 指定关键词高亮格式
                .withPageable(PageRequest.of(0, 10));  //  按分页查询（第几页，显示几条数据）
        CriteriaQuery query = new CriteriaQuery(builder);

        // 通过elasticsearchTemplate查询
        SearchHits<DiscussPost> result = elasticTemplate.search(query, DiscussPost.class);  // 传入查询条件和实体类型

        // 处理结果
        List<SearchHit<DiscussPost>> searchHitList = result.getSearchHits();
        List<DiscussPost> discussPostList = new ArrayList<>();
        if(searchHitList != null){
            for (SearchHit<DiscussPost> hit : searchHitList) {
                // 遍历每个搜索命中对象
                DiscussPost post = hit.getContent();
                // 将高亮结果添加到返回的结果类中显示
                var titleHighlight = hit.getHighlightField("title");  // 获取标题中的高亮字段“互联网寒冬”
                if (titleHighlight.size() != 0) {
                    post.setTitle(titleHighlight.get(0));
                }
                var contentHighlight = hit.getHighlightField("content");
                if (contentHighlight.size() != 0) {
                    post.setContent(contentHighlight.get(0));
                }
                discussPostList.add(post);
            }
        }

        // 构建Page对象
        Page<DiscussPost> page = new PageImpl<>(discussPostList, PageRequest.of(1, 10), result.getTotalHits());

        // 打印结果
        System.out.println(result.getTotalHits());
        for (DiscussPost post : page) {
            System.out.println(post);
        }
    }
}