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

## 9. spy()
- spy：基于真实对象的代理，未打桩的方法会执行真实逻辑
- mock：完全假的对象，方法默认不执行真实逻辑
- 当需要保留部分真实逻辑时使用 spy()
```kotlin
val realList = mutableListOf<String>()
val spyList = spy(realList)
doReturn(100).`when`(spyList).size
```




