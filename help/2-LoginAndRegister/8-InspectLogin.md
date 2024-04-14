# 检查登录状态

- 使用拦截器
  - 在方法前标注自定义注解
  - 拦截所有请求，只处理带有该注解的方法
- 自定义注解
  - 常用元注解：`@Target, @Retention, @Document, @Inherited`
  - 如何读取注解：
    - `Method.getDeclaredAnnotations()`
    - `Method.getAnnotation(Class<T> annotationClass)`

##