1. 协程的启动方式有哪些？

   ```kotlin
   launch { }     // 不会返回结果，适合执行任务
   async { }      // 返回Deferred<T>，适合并发获取结果
   runBlocking { } // 阻塞当前线程，通常用于测试或main函数
   
   ```

2. Kotlin 高阶函数 和 作用域函数有哪些？区别？

	✅ 高阶函数：接收函数作为参数或返回函数的函数

	常见：`map`、`filter`、`run`、`with`、`let`、`apply`

	✅ 作用域函数：

| 函数名  | 返回值     | this/it | 适用场景           |
| ------- | ---------- | ------- | ------------------ |
| `let`   | Lambda结果 | it      | 链式调用、非空处理 |
| `run`   | Lambda结果 | this    | 初始化、表达式     |
| `with`  | Lambda结果 | this    | 多次调用同一对象   |
| `apply` | 对象本身   | this    | 初始化对象         |
| `also`  | 对象本身   | it      | 调试、日志打印     |

3. 如何创建静态函数、变量？怎么写单例？

   ✅ 静态方法/变量：

   ```kotlin
   object Utils {
       fun log() {}
       val VERSION = "1.0"
   }
   ```

   或使用 `companion object {}` 创建类内部静态成员。

   

   ✅ 单例：

   ```kotlin
   object MySingleton {
       fun doSomething() { }
   }
   ```

4. lateinit vs lazy 的区别？

   | 特性         | `lateinit`               | `lazy`                    |
   | ------------ | ------------------------ | ------------------------- |
   | 用于类型     | `var`，非空引用          | `val`，不可变             |
   | 适用场景     | 需要延后初始化（如View） | 惰性初始化（如配置/单例） |
   | 是否线程安全 | 否                       | 默认线程安全（可配置）    |

5. 什么是内联函数？

   使用 `inline` 修饰的函数。将函数体编译期间替换到调用处，**减少函数对象创建和性能开销**，常用于高阶函数。

   ```kotlin
   inline fun doSomething(block: () -> Unit) {
       block()
   }
   ```

6. 网络请求如何实现？Retrofit 加 Header？拦截器？

   #### ✅ 实现方式：

   - Retrofit + OkHttp + 协程 或 RxJava

   ✅ 每个请求添加 Header：

   ```kotlin
   @GET("xxx")
   fun getData(@Header("token") token: String): Call<Response>
   ```

   或全局添加：

   ```kotlin
   val client = OkHttpClient.Builder()
       .addInterceptor { chain ->
           val request = chain.request().newBuilder()
               .addHeader("token", "yourToken")
               .build()
           chain.proceed(request)
       }.build()
   ```

   #### ✅ 拦截器：

   - **应用拦截器（addInterceptor）**：不会受缓存控制影响，可做日志、统一Header处理
   - **网络拦截器（addNetworkInterceptor）**：处理请求与响应的原始数据，可用于缓存控制

7. 模块化开发时，模块如何通信？

   我们模块间数据通信主要通过数据库和事件机制来解耦，比如：

   - 数据层统一管理，模块写入 Room 后，通过 Flow 或 LiveData 传递给 UI；
   - 使用 Hilt 提供依赖注入，方便跨模块复用；
   - 一些跨模块事件用 EventBus 或协程通道来处理。

8. 项目模块化结构？你的角色？

   项目按功能拆分多个 module，比如 `login`、`home`、`network`、`common`。

   - 公共模块（如网络库、数据存储）用 Base 模块隔离；
   - 使用 Gradle 配置依赖，提升构建效率；
   - 我在项目中主要负责【模块A/B】的核心功能开发，同时也参与了基础架构的搭建（如 Hilt、Retrofit 封装等）。

9. kotlin 冷流和热流的区别？

   Kotlin 中的**冷流（Cold Flow）\**和\**热流（Hot Flow）\**是 `Flow` 的两种核心行为方式，它们的区别主要体现在\**数据的生产时机**和**多个订阅者之间的数据共享**。

   🔹 一句话总结

   | 类型 | 行为特征                                 | 示例                      |
   | ---- | ---------------------------------------- | ------------------------- |
   | 冷流 | 每次收集时都重新执行数据生产逻辑         | `flow { ... }`            |
   | 热流 | 数据一直在产生，收集者只能接收之后的数据 | `SharedFlow`, `StateFlow` |

	🧊 冷流（Cold Flow）

		冷流是懒加载的（**冷启动**）：只有在有人收集（`collect`）时，才开始执行流的逻辑。

		每个收集者**都能从头开始**接收完整的数据序列。

		✅ 每次 collect 都会触发一次 flow 的执行逻辑。

	🔥 热流（Hot Flow）

		热流则是数据**主动推送**的，不依赖 collect 的调用，数据是持续存在的，一旦发送数据，**当前活跃的收集者**才能接收到。

		常见热流类型：

| 类型         | 特点                                                    |
| ------------ | ------------------------------------------------------- |
| `StateFlow`  | 类似于 LiveData，有初始值、保存最新值，只发给活跃收集者 |
| `SharedFlow` | 类似于广播，不保留值，可配置缓存和重放策略              |

	🆚 冷流 vs 热流 区别汇总表

| 比较项       | 冷流（Cold Flow）        | 热流（Hot Flow）             |
| ------------ | ------------------------ | ---------------------------- |
| 是否懒执行   | 是（按需）               | 否（主动推送）               |
| 是否共享数据 | 否，每个收集者独立       | 是，多个收集者共享同一个源头 |
| 数据发射时机 | 收集时才发射             | 创建后就可发射，和收集无关   |
| 是否保留数据 | 否                       | `StateFlow` 可保留最新数据   |
| 类比         | Retrofit 请求、Room 查询 | LiveData、事件总线           |

	✍ 实际开发中怎么用？

	**冷流**：用于一次性获取数据，比如网络请求、数据库查询（结合 `flow {}` 使用）。

	**热流**：用于状态共享、通知、事件总线。

- `StateFlow` → 替代 `LiveData`；
- `SharedFlow` → 用于事件分发，比如 toast、导航等。

	写法

	✅ 正确写法分析（冷流）

```kotlin
viewModel.someFlow
.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
.onEach { value ->
    // 处理每个值，比如更新 UI
}
.launchIn(viewLifecycleOwner.lifecycleScope)
```

	✅ 优点：

- `flowWithLifecycle` 会根据 Fragment 的生命周期自动暂停/恢复 collect，避免内存泄漏。
- `onEach` 可以链式处理每个发射的值。
- `launchIn(scope)` 会自动启动冷流。

	🔥 冷流启动关键知识点

	冷流是「惰性」的，必须 `collect` 才会启动。

	Kotlin Flow 有四种终结操作（表示“启动 collect 的动作”）：

| 操作符            | 用途                                |
| ----------------- | ----------------------------------- |
| `collect`         | 最基础的收集                        |
| `collectLatest`   | 收集最新值（会取消旧的）            |
| `launchIn(scope)` | 启动冷流协程，适合链式使用          |
| `toList()`        | 把 flow 变成 list，常用于一次性收集 |

```kotlin
viewModel.uiState
    .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
    .onEach { state ->
        // 处理 UI 更新
    }
    .launchIn(viewLifecycleOwner.lifecycleScope)

```