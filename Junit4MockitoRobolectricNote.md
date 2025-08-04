# JUnit4 + Mockito + Robolectric 单元测试笔记

## 1. 测试流程（Arrange / Act / Assert）

1. **Arrange 准备**
   - 构造被测类对象
   - 使用 `mock()` 创建依赖对象
   - 使用 `when/thenReturn` 或 `doThrow` 模拟依赖行为

2. **Act 执行**
   - 调用目标方法

3. **Assert 断言**
   - 验证方法调用 `verify(...)`
   - 验证返回值 `assertEquals / assertTrue`
   - 验证抛出的异常 `assertThrows`

---

## 2. Mock 对象
```kotlin
val mockObj = mock(类名::class.java)
```
或使用注解：
```kotlin
@Mock lateinit var obj: 类名
```
## 3. 验证方法行为
```kotlin
verify(mockObj).方法(any())
verify(mockObj, times(1)).方法(any())
verify(mockObj, never()).方法(any())
```
## 4. 验证方法返回值
```kotlin
assertTrue(result)
assertEquals(expected, actual)
```
## 5. 异常验证
推荐 assertThrows：
```kotlin
assertThrows(目标异常::class.java) {
    被测对象.方法(参数)
}
``` 
老写法 `@Test(expected=...)` 不推荐。





