package com.nowcoder.community.service;

import com.nowcoder.community.dao.elasticsearch.DiscussPostRepository;
import com.nowcoder.community.entity.DiscussPost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.CriteriaQueryBuilder;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ElasticsearchService {

    @Autowired
    private DiscussPostRepository discussRepository;  // dao

    @Autowired
    private ElasticsearchTemplate elasticTemplate;

    // 保存与修改（新的save相当于修改）
    public void saveDiscussPost(DiscussPost post){
        discussRepository.save(post);
    }

    // 删除
    public void deleteDiscussPost(int id){
        discussRepository.deleteById(id);
    }

    // 搜索，可以复用上次的test代码
    public Page<DiscussPost> searchDiscussPost(String keyword, int current, int limit){  // 分页传参：第几页和每页条数
        Criteria criteria = new Criteria("title").matches(keyword).or(new Criteria("content").matches(keyword));

        // 这里的高亮格式还是<em></em>，但是前端的global.css已经处理为了高亮红色
        List<HighlightField> highlightFieldList = new ArrayList<>();
        HighlightField highlightField = new HighlightField("title", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
        highlightFieldList.add(highlightField);
        highlightField = new HighlightField("content", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
        highlightFieldList.add(highlightField);
        Highlight highlight = new Highlight(highlightFieldList);
        HighlightQuery highlightQuery = new HighlightQuery(highlight, DiscussPost.class);

        CriteriaQueryBuilder builder = new CriteriaQueryBuilder(criteria)
                .withSort(Sort.by(Sort.Direction.DESC, "type"))
                .withSort(Sort.by(Sort.Direction.DESC, "score"))
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
                .withHighlightQuery(highlightQuery)
                .withPageable(PageRequest.of(current, limit));
        CriteriaQuery query = new CriteriaQuery(builder);

        SearchHits<DiscussPost> result = elasticTemplate.search(query, DiscussPost.class);
        if(result.isEmpty()){
            return null;
        }

        List<SearchHit<DiscussPost>> searchHitList = result.getSearchHits();
        List<DiscussPost> discussPostList = new ArrayList<>();
        for (SearchHit<DiscussPost> hit : searchHitList) {
            DiscussPost post = hit.getContent();
            var titleHighlight = hit.getHighlightField("title");
            if (titleHighlight.size() != 0) {
                post.setTitle(titleHighlight.get(0));
            }
            var contentHighlight = hit.getHighlightField("content");
            if (contentHighlight.size() != 0) {
                post.setContent(contentHighlight.get(0));
            }
            discussPostList.add(post);
        }

        return new PageImpl<>(discussPostList, PageRequest.of(current, limit), result.getTotalHits());
    }

}
