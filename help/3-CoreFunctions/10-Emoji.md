# 自己实现——发表情功能实现

1. 修改数据库表的格式，使之能够存储表情格式：

```sql
ALTER TABLE `comment` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `comment` MODIFY `content` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `discuss_post` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `discuss_post` MODIFY `content` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `message` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `message` MODIFY `content` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

现在谷歌浏览器右键鼠标即可发表情了，但是还不够方便，我们在输入框都加上表情的快捷键

2. 前端部分

letter-detail和letter（两个类似，都是给发消息的框加个表情按钮就行）:

```html
<style>
    .emoji-picker {
        z-index: 1050;
    }
</style>

<div class="form-group">
    <label for="message-text" class="col-form-label">内容：</label>
    <textarea class="form-control" id="message-text" rows="10"></textarea>
    <button type="button" id="emoji-button-message" class="btn btn-light">😊</button>
</div>

<script src="https://unpkg.com/emoji-button@latest/dist/index.js"></script>
<script>
    function back() {
        location.href = CONTEXT_PATH + "/letter/list";
    }

    document.addEventListener('DOMContentLoaded', function () {
        const button = document.querySelector('#emoji-button-message');
        const picker = new EmojiButton();

        picker.on('emoji', emoji => {
            const textarea = document.querySelector('#message-text');
            textarea.value += emoji;
        });

        button.addEventListener('click', () => {
            picker.showPicker(button);
        });
    });
</script>
```

discuss-detail.html:

```html
<form method="post" th:action="@{|/comment/add/${post.id}|}">
    <div>
        <input type="text" class="input-size" name="content" th:placeholder="|回复 ${rvo.user.username}|">
        <input type="hidden" name="entityType" value="2"> <!--评论-->
        <input type="hidden" name="entityId" th:value="${cvo.comment.id}">
        <input type="hidden" name="targetId" th:value="${rvo.user.id}">
        <button type="button" class="btn btn-light btn-sm emoji-button-reply">😊</button>
    </div>
    <div class="text-right mt-2">
        <button type="submit" class="btn btn-primary btn-sm">&nbsp;&nbsp;回&nbsp;&nbsp;复&nbsp;&nbsp;</button>
    </div>
</form>

<form method="post" th:action="@{|/comment/add/${post.id}|}">
    <div>
        <textarea class="input-size" name="content" placeholder="和大家一起讨论吧~ @用户记得加空格哦~" required></textarea>
        <input type="hidden" name="entityType" value="2"> <!--评论-->
        <input type="hidden" name="entityId" th:value="${cvo.comment.id}">
        <button type="button" class="btn btn-light btn-sm emoji-button-reply">😊</button>
    </div>
    <div class="text-right mt-2">
        <button type="submit" class="btn btn-primary btn-sm">&nbsp;&nbsp;回&nbsp;&nbsp;复&nbsp;&nbsp;</button>
    </div>
</form>

<form class="replyform" method="post" th:action="@{|/comment/add/${post.id}|}">
<p class="mt-3">
    <a name="replyform"></a>
    <textarea placeholder="在这里畅所欲言你的看法吧! @用户记得加空格哦~" name="content" id="comment-content" required></textarea>
    <input type="hidden" name="entityType" value="1"> <!--帖子-->
    <input type="hidden" name="entityId" th:value="${post.id}">
    <button type="button" id="emoji-button-comment" class="btn btn-light">😊</button>
</p>
<p class="text-right">
    <button type="submit" class="btn btn-primary btn-sm">&nbsp;&nbsp;评&nbsp;&nbsp;论&nbsp;&nbsp;</button>
</p>
</form>

<script>
    document.addEventListener('DOMContentLoaded', function () {
        const button = document.querySelector('#emoji-button-comment');
        const picker = new EmojiButton();

        picker.on('emoji', emoji => {
            const textarea = document.querySelector('#comment-content');
            textarea.value += emoji;
        });

        button.addEventListener('click', () => {
            picker.showPicker(button);
        });
    });
    document.addEventListener('DOMContentLoaded', function () {
        const buttons = document.querySelectorAll('.emoji-button-reply');

        buttons.forEach(button => {
            const picker = new EmojiButton();
            const form = button.closest('form');

            picker.on('emoji', emoji => {
                const textarea = form.querySelector('textarea');
                const inputField = form.querySelector('input[name="content"]');
                if (textarea) {
                    textarea.value += emoji;
                } else if (inputField) {
                    inputField.value += emoji;
                }
            });

            button.addEventListener('click', () => {
                picker.showPicker(button);
            });
        });
    });
</script>
```

publish和update-posts界面：

```html
<style>
    .emoji-picker {
        z-index: 1050;
    }
</style>

<div class="text-right mt-2">
    <button type="button" class="btn btn-light btn-sm emoji-button">😊</button>
</div>

<script src="https://unpkg.com/emoji-button@latest/dist/index.js"></script>
<script>
    $(function() {
        $("#publishBtn").click(publish);
        $("#backIndexBtn").click(backIndex);

        const button = document.querySelector('.emoji-button');
        const picker = new EmojiButton();

        picker.on('emoji', emoji => {
            testEditor.insertValue(emoji);
        });

        button.addEventListener('click', () => {
            picker.showPicker(button);
        });
    });
</script>
```