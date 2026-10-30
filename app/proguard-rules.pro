# Compose 自带 consumer rules，这里只补一点保底项
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# 我们的数据类不需要额外规则（全程无反射序列化）
