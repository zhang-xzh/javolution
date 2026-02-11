# Javolution IO (Trimmed: Struct/Union)

这是一个精简版的 **纯 Java** 二进制结构体/联合体实现，用于把数据映射到 `java.nio.ByteBuffer`，
并方便地在 **socket / 文件 / 任意字节流** 中读写，供 C/C++（或其他语言）按相同协议解析。

本仓库只保留 `org.javolution.io` 下的核心代码：

- `Struct`：类似 C/C++ `struct` 的内存布局（对齐/填充/bit-field/嵌套/数组）
- `Union`：类似 C/C++ `union`（所有成员从同一内存位置开始并重叠）
- `UTF8ByteBufferReader` / `UTF8ByteBufferWriter`：基于 `ByteBuffer` 的 UTF-8 编解码辅助
- `package-info.java`：包说明

不包含 OSGi、Context 框架、集合、XML 等其它模块。

---

## 构建

### 环境
- JDK 25
- Maven

### 命令
```
bash
mvn -q clean package
```
---

## 快速上手

### 1) 定义一个 Struct

**成员声明顺序决定布局**（与 C 端字段顺序对应）。
```
java
import org.javolution.io.Struct;
import java.nio.ByteOrder;

public class Header extends Struct {
    public final Unsigned16 length = new Unsigned16();
    public final Unsigned32 seq    = new Unsigned32();

    @Override
    public ByteOrder byteOrder() {
        return ByteOrder.BIG_ENDIAN; // 需与协议/对端一致
    }
}
```
### 2) 写入输出流（例如 socket）
```
java
Header h = new Header();
h.length.set(42);
h.seq.set(1234);

h.write(outputStream);
```
### 3) 从输入流读取（例如 socket）
```
java
Header h = new Header();
h.read(inputStream);

int length = h.length.get();
long seq   = h.seq.get();
```
### 4) 定义一个 Union

Union 的所有成员共享同一段内存：写入一个视图，会影响其它视图的读取结果。
```
java
import org.javolution.io.Union;

public class Number extends Union {
    public final Signed32   asInt    = new Signed32();
    public final Float32    asFloat  = new Float32();
    public final UTF8String asString = new UTF8String(12);
}
```
---

## 与 C/C++ 对接时必须明确的约定

### 1) 字节序（ByteOrder）
`Struct` 使用 `byteOrder()` 控制多字节数值的字节序。务必与对端一致：
```
java
@Override
public ByteOrder byteOrder() {
    return ByteOrder.LITTLE_ENDIAN; // 或 BIG_ENDIAN
}
```
### 2) 对齐与 packing
- 默认 `isPacked() == false`：按常见 C 对齐规则插入 padding
- 若 C 端使用 `#pragma pack(1)` 等，请在 Java 端覆盖：
```
java
@Override
public boolean isPacked() {
    return true;
}
```
### 3) JVM 上的无符号表示
JVM 基础整数类型是有符号的；本实现通过“更宽的有符号类型”表达常见无符号范围：

- `Unsigned8.get()`  → `short`（0..255）
- `Unsigned16.get()` → `int`（0..65535）
- `Unsigned32.get()` → `long`（0..4294967295）

### 4) ByteBuffer 选择
如未显式设置 buffer，`Struct` 会按 `size()` 分配一个 **direct** `ByteBuffer` 并设置字节序。
你也可以提供自己的 buffer（heap 或 direct）：
```
java
Header h = new Header();
h.setByteBuffer(java.nio.ByteBuffer.allocate(h.size()).order(h.byteOrder()), 0);
```
---

## 重要限制（基于当前实现）

### `Struct.address()` 在本精简版中不可用
当前 `Struct.address()` 会抛出 `UnsupportedOperationException`，以保持“纯 Java 传输”定位，不暴露 native 地址。
因此 `Reference32/Reference64` 这类“指针/地址映射”用途不适用于本精简版。

### `MAXIMUM_ALIGNMENT`
`Struct.MAXIMUM_ALIGNMENT` 目前仅作为常量/预留点保留，未参与布局计算。

---

## License

MIT — see `LICENSE`.
