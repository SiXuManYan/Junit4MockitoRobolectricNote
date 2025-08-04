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

## 6. 模拟依赖返回值和异常
有返回值
```kotlin
whenever(mockObj.方法(any())).thenReturn(值)
whenever(mockObj.方法(any())).thenThrow(RuntimeException())
```
无返回值
```kotlin
doThrow(RuntimeException()).when(mockObj).无返回值方法(any())
```

## 7. 测试finally 中的逻辑
- finally 块无论是否异常都会执行，一般无需单独测试
- 如果 finally 中调用了 静态方法，可用 mockStatic() 验证：
```kotlin
mockStatic(工具类::class.java).use { mocked ->
    assertThrows(异常::class.java) { 被测对象.方法() }
    mocked.verify { 工具类.静态方法() }
}
```

## 8. any() 和 mock() 的区别
- **any()**  
  - 参数匹配器  
  - 只在 `when` 或 `verify` 中使用，不会创建对象  
- **mock()**  
  - 创建假的对象，需要手动传入  

### 验证参数内容
使用 `ArgumentCaptor` 捕获参数：
```kotlin
val captor = argumentCaptor<类型>()
verify(mockObj).方法(captor.capture())
assertEquals(期望值, captor.firstValue.字段)
```

## 9. spy() 和 mock()区别
- spy：基于真实对象的代理，未打桩的方法会执行真实逻辑，（保留真实逻辑，只针对需要控制的点打桩）。
- mock：完全假的对象，方法默认不执行真实逻辑
- 当需要保留部分真实逻辑时使用 spy()
```kotlin
val realList = mutableListOf<String>()
val spyList = spy(realList)
doReturn(100).`when`(spyList).size
```

## 10. stub（打桩）
Stub：预设方法返回值或行为，避免执行真实逻辑
- 常用写法：
```kotlin
whenever(mockObj.method(param)).thenReturn(value)
doThrow(RuntimeException()).`when`(mockObj).voidMethod()
```
- 对于 spy 对象，未打桩的方法默认会执行真实逻辑

## 11. doAnswer()
用于在 mock 方法被调用时：
- 动态访问参数
- 执行自定义逻辑（如触发回调）
- 决定返回值或抛出异常
```kotlin
doAnswer {
    val listener = it.arguments[0] as Listener
    listener.onEvent()
    null
}.`when`(mockObj).注册监听(any())
```

## 12. Robolectric
 - 使用 RobolectricTestRunner 可在 JVM 直接运行 Android 测试，无需真机
```kotlin
@RunWith(RobolectricTestRunner::class)
class ExampleTest {
    @Test
    fun testSomething() { ... }
}
```

## 13. 推荐的单元测试模板
```kotlin
class ExampleTest {

    private lateinit var adapter: 被测类
    private lateinit var dependency: 依赖接口

    @Before
    fun setUp() {
        dependency = mock(依赖接口::class.java)
        adapter = 被测类().apply {
            this.依赖 = dependency
        }
    }

    @Test
    fun testSuccess() {
        // Arrange
        whenever(dependency.方法()).thenReturn(预设数据)

        // Act
        adapter.方法()

        // Assert
        verify(dependency).方法()
    }

    @Test
    fun testThrowException() {
        whenever(dependency.方法()).thenThrow(RuntimeException())

        assertThrows(目标异常::class.java) {
            adapter.方法()
        }
    }
}

```

## 14.命名建议
```
test方法名_场景_期望结果
例如：
testLogin_withInvalidPassword_throwException
```

## 15.关键要点总结
- 测试结构遵循 Arrange / Act / Assert
- 使用 mock/spy/when/doThrow/doAnswer 控制依赖
- 优先使用 assertThrows 验证异常
- Robolectric 适合本地 JVM 环境运行 Android 逻辑





