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

```