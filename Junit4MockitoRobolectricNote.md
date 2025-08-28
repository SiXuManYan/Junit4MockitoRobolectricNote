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

## 16.常用 JUnit4 + Mockito + Robolectric 注解简述
### @RunWith
- **作用**  
  指定运行测试类的 Runner。  
  在 Android 单元测试中常用：
  - `@RunWith(MockitoJUnitRunner::class)`：使用 Mockito 的 Runner。
  - `@RunWith(RobolectricTestRunner::class)`：使用 Robolectric 的 Runner。
- **注意**  
  Robolectric 测试类必须使用 `RobolectricTestRunner`。

### @Before
- **作用**  
  在每个 `@Test` 方法执行前运行，用于公共初始化逻辑。

- **示例**  
  ```java
  @Before
  public void setUp() {
      MockitoAnnotations.initMocks(this);
  }
  ```
### @After
- 作用
在每个 @Test 方法执行后运行，用于释放资源、重置状态。

### @Test
- 作用
标识测试方法。
- 可选参数
  - `expected = Exception.class`：期望抛出某异常。
  - `timeout = 1000`：设置超时时间。
  - 
### @Mock
- 作用
使用 Mockito 创建一个 Mock 对象。
- 特点
不需要手动实例化对象，Mockito 会自动生成代理。

### @InjectMocks
- 作用
自动将`@Mock` 创建的依赖对象注入到待测试的类中。
- 原理
Mockito 会基于构造函数、Setter 或字段注入。

### 其他相关注解（扩展）
- @Config (Robolectric 提供)
   - 用于指定 SDK 版本、资源路径等。
   - 示例：
     ```java
     @Config(sdk = 29)
     ```

### 示例
```java
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class ExampleTest {

    @Mock
    MyRepository repository;

    @InjectMocks
    MyViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void tearDown() {
        // 清理资源
    }

    @Test
    public void testSomething() {
        // 使用 mock 数据进行测试
    }
}

```

## 17. private 私有函数直接mock
```java
    @SuppressWarnings("unchecked")
    private boolean invokeXXXX(XXXClass className, XXXParams params) throws Exception {
        Method method =
                XXXClass.class.getDeclaredMethod("XXXMethodName", XXXParams.class);
        method.setAccessible(true);
        return (返回值类型) method.invoke(className, params);
    }

    /**
     * map == null → false
     */
    @Test
    public void test_nullMap_rerurnFalse() throws Exception {
        boolean result = invokeXXXX(card, null);
        assertFalse(result);
    }
```
## 18. 强制mockpublic函数，不走内部逻辑，执行返回值时：应将所需参数，也带上。参数不能是any
```java
   Result result = obj.judgeGacResultFromEmv( mCardInfo.getEmvTags());
```
```java
   Map<Integer, String> emvTags = mCardInfo.getEmvTags();
   doReturn(SUCCESS_IC_TC)
           .when(obj)
           .judgeGacResultFromEmv(emvTags); // 注意此处不能为 anyMap
```


---
# 测试中的关键知识点整理

## 1. 本地单元测试 vs 真机测试
- 本地单元测试（JVM / Robolectric）：
  - 无法执行真实的服务绑定
  - 适合验证字段初始化（上下文、线程、Handler 等）
  - 绑定服务相关的状态变化不会成功
- 真机 / Instrumentation 测试：
  - 可以正常绑定服务并收到回调
  - 可以验证服务状态的变化

## 2. 覆盖异常分支的方法
- 思路：让某个被调用的初始化方法抛出 `IllegalArgumentException`
- 方法：
  1. 先正常调用一次初始化，忽略第一次绑定失败的异常
  2. 使用反射把内部依赖替换成 mock
  3. mock 的方法抛出 `IllegalArgumentException`
  4. 再通过反射直接调用私有方法，进入 catch 分支

## 3. Context 获取方式
- 本地测试：`ApplicationProvider.getApplicationContext()`
- 真机测试：`InstrumentationRegistry.getInstrumentation().getTargetContext()`

## 4. 适用结论
- 本地测试：只验证字段初始化
- 异常分支：通过 mock 和反射单独触发
- 真机测试：验证完整连接流程和回调

---



