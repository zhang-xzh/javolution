/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2012 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */
package org.javolution.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * <p> 等同于 <code>C/C++ struct</code>；此类赋予了
 *     Java 类与 C/C++ 结构体之间的互操作性。</p>
 *
 * <p> 与 <code>C/C++</code> 不同，Java 对象的存储布局不由编译器决定。
 *     对象在内存中的布局被推迟到运行时，并由解释器（或即时编译器）决定。
 *     这种方法允许动态加载和绑定；但也使得与 <code>C/C++</code> 代码的接口变得困难。
 *     因此，此类的内存布局由 {@link Struct} 的 {@link Member 成员} 的初始化顺序定义，
 *     并遵循与 <code>C/C++ 结构体</code> 相同的字大小（wordSize）规则。</p>
 *
 * <p> 此类（以及 {@link Union} 子类）便于：</p>
 *     <ul>
 *     <li> Java 应用程序与原生库之间的内存共享。</li>
 *     <li> 对其结构由传统 C/C++ 代码定义的流进行直接编码/解码。</li>
 *     <li> Java 对象的序列化/反序列化（完全控制，例如没有类头）。</li>
 *     <li> 将 Java 对象映射到物理地址（通过 JNI）。</li>
 *     </ul>
 *
 * <p> 由于它具有一对一的映射关系，因此使用简单的文本宏将 C 头文件（例如 OpenGL 绑定）
 *     转换为 Java {@link Struct}/{@link Union} 相对容易。以下是 C 结构体的示例：</p>
 * [code]
 * enum Gender{MALE, FEMALE};
 * struct Date {
 *     unsigned short year;
 *     unsigned byte month;
 *     unsigned byte day;
 * };
 * struct Student {
 *     enum Gender gender;
 *     char        name[64];
 *     struct Date birth;
 *     float       grades[10];
 *     Student*    next;
 * };[/code]
 * <p> 对应的 Java 等效类如下：</p>
 * [code]
 * public enum Gender { MALE, FEMALE };
 * public static class Date extends Struct {
 *     public final Unsigned16 year = new Unsigned16();
 *     public final Unsigned8 month = new Unsigned8();
 *     public final Unsigned8 day   = new Unsigned8();
 * }
 * public static class Student extends Struct {
 *     public final Enum32<Gender> gender = new Enum32<>(Gender.values());
 *     public final UTF8String name = new UTF8String(64);
 *     public final Date birth = inner(new Date());
 *     public final Float32[] grades = array(new Float32[10]);
 * }[/code]
 * <p> Struct 的成员可以直接访问：
 * {@code
 * Student student = new Student();
 * student.gender.set(Gender.MALE);
 * student.name.set("John Doe"); // Null 终止（C 兼容）
 * int age = 2003 - student.birth.year.get();
 * student.grades[2].set(12.5f);
 * student = student.next.get();}</p>
 *
 * <p> 应用程序可以直接处理原始的 {@link #getByteBuffer() 字节}。
 *     以下说明了如何使用 {@link Struct} 直接解码/编码 UDP 消息：
 * {@code
 * class UDPMessage extends Struct {
 *      Unsigned16 xxx = new Unsigned16();
 *      ...
 * }
 * public void run() {
 *     byte[] bytes = new byte[1024];
 *     DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
 *     UDPMessage message = new UDPMessage();
 *     message.setByteBuffer(ByteBuffer.wrap(bytes), 0);
 *         // 现在 packet 和 message 是相同数据的两个不同视图。
 *     while (isListening) {
 *         multicastSocket.receive(packet);
 *         int xxx = message.xxx.get();
 *         ... // 直接处理消息字段。
 *     }
 * }}</p>
 *
 * <p> 使用 <a href="http://java.sun.com/docs/books/tutorial/native1.1/index.html">
 *     JNI</a> 可以相对容易地将此类的实例映射到任何物理地址。以下是一个示例：</p>
 * {@code
 * import java.nio.ByteBuffer;
 * class Clock extends Struct { // 映射到内存的硬件时钟。
 *     Unsigned16 seconds  = new Unsigned16(5); // unsigned short seconds:5
 *     Unsigned16 minutes  = new Unsigned16(5); // unsigned short minutes:5
 *     Unsigned16 hours    = new Unsigned16(4); // unsigned short hours:4
 *     Clock() {
 *         setByteBuffer(Clock.nativeBuffer(), 0);
 *     }
 *     private static native ByteBuffer nativeBuffer();
 * }}</p>
 *  <p> 以下是 <code>nativeBuffer()</code> 的实现 (<code>Clock.c</code>)：
 *  {@code
 *  #include <jni.h>
 *  #include "Clock.h" // 使用 javah 生成
 *  JNIEXPORT jobject JNICALL Java_Clock_nativeBuffer (JNIEnv *env, jclass) {
 *      return (*env)->NewDirectByteBuffer(env, clock_address, buffer_size)
 *  }}</p>
 *
 * <p> 支持位域（参见上面的 <code>Clock</code> 示例）。
 *     位域分配顺序由 Struct 的 {@link #byteOrder} 返回值定义。
 *     如果是 <code>BIG_ENDIAN</code>，则从最高位到最低位分配；
 *     如果是 <code>LITTLE_ENDIAN</code>，则从最低位到最高位分配
 *     （与 Microsoft Visual C++ 布局相同）。
 *     C/C++ 位域不能跨越由其基础类型定义的存储单元边界
 *     （在第一个位域末尾插入填充，并将第二个位域放入下一个存储单元）。
 *     可以通过使用 {@link BitField} 成员（或其子类）来避免位填充。
 *     在这种情况下，分配顺序始终是从最高位到最低位（与 <code>BIG_ENDIAN</code> 相同）。
 *     </p>
 *
 * <p> 最后，可以通过更改 Struct 在其 <code>ByteBuffer</code> 中的 {@link #setByteBuffer ByteBuffer}
 *     和/或 {@link #setByteBufferPosition position}，从而允许单个 {@link Struct} 对象
 *     对多个内存映射实例进行编码/解码。</p>
 *
 * <p><i>注意：由于 Struct/Union 基本上是 <code>java.nio.ByteBuffer</code> 的包装器，
 *             Java NIO 包的教程/用法直接适用于 Struct/Union。</i></p>
 *
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @version 5.5.1, April 1, 2010
 */
@SuppressWarnings({"unchecked", "unused", "UnusedReturnValue"})
public class Struct {

    /**
     * 可配置项，持有以字节为单位的最大字大小（wordSize）
     * （默认为 <code>4</code>）。应为大于或等于 1 的值。
     */
    @SuppressWarnings("unused")
    public static final int MAXIMUM_ALIGNMENT = 4;

    /**
     * 持有外部结构体（如果有）。
     */
    Struct _outer;
    /**
     * 持有支持该结构体的字节缓冲区（顶层结构体）。
     */
    ByteBuffer _byteBuffer;
    /**
     * 持有此结构体相对于外部结构体的偏移量，
     * 如果没有外部结构体，则相对于字节缓冲区。
     */
    int _outerOffset;
    /**
     * 持由此结构体的字节对齐方式（其成员中最大的字大小）。
     */
    int _alignment = 1;
    /**
     * 持由此结构体的长度。
     */
    int _length;
    /**
     * 持有构造期间的索引位置。
     * 这是第一个可用的未使用字节的索引。
     */
    int _index;
    /**
     * 持有构造期间的字大小（用于位域）。
     * 这是最后使用的字的大小。
     */
    int _wordSize;
    /**
     * 持有构造期间字中已使用的位数（用于位域）。
     * 这是最后一个字中使用的位数。
     */
    int _bitsUsed;
    /**
     * 指示是否为每个新字段重置索引（
     * 仅对于 Union 子类为 <code>true</code>）。
     */
    boolean _resetIndex;
    /**
     * 当 byteBuffer 没有固有的数组时，持有用于流 I/O 的字节数组。
     */
    byte[] _bytes;

    /**
     * 默认构造函数。
     */
    public Struct() {
        _resetIndex = isUnion();
    }

    /**
     * 返回此结构体的字节大小。该大小包括尾部填充，
     * 以满足结构体字大小要求（由其 {@link Member 成员} 中最大的字大小定义）。
     *
     * @return C/C++ 中的 <code>sizeof(this)</code>。
     */
    public final int size() {
        return (_alignment <= 1) ? _length
                : ((_length + _alignment - 1) / _alignment) * _alignment;
    }

    /**
     * 返回此结构体的外部结构体，如果此结构体不是内部结构体，则返回 <code>null</code>。
     *
     * @return 外部结构体或 <code>null</code>。
     */
    public Struct outer() {
        return _outer;
    }

    /**
     * 返回此结构体的字节缓冲区。如果尚未设置，此方法将分配一个新的<b>直接（direct）</b>缓冲区。
     *
     * <p> 对缓冲区内容的修改在此结构体中可见，反之亦然。</p>
     * <p> 内部结构体的缓冲区与其父结构体相同。</p>
     * <p> 如果尚未通过 {@link Struct#setByteBuffer set} 设置字节缓冲区，
     *     则会分配一个容量等于此结构体 {@link Struct#size() 大小} 的直接缓冲区。</p>
     *
     * @return 当前字节缓冲区，如果未设置则返回一个新的直接缓冲区。
     * @see #setByteBuffer
     */
    public final ByteBuffer getByteBuffer() {
        if (_outer != null) return _outer.getByteBuffer();
        return (_byteBuffer != null) ? _byteBuffer : newBuffer();
    }

    private synchronized ByteBuffer newBuffer() {
        if (_byteBuffer != null) return _byteBuffer; // Synchronized check.
        ByteBuffer bf = ByteBuffer.allocateDirect(size());
        bf.order(byteOrder());
        setByteBuffer(bf, 0);
        return _byteBuffer;
    }

    /**
     * 为此结构体设置当前的字节缓冲区。
     * 指定的字节缓冲区可以映射到内存以进行直接内存访问，
     * 也可以包装一个共享字节数组用于 I/O 目的（例如 <code>DatagramPacket</code>）。
     * 指定字节缓冲区的容量应至少为此结构体的 {@link Struct#size() 大小} 加上偏移位置。
     *
     * @param byteBuffer 新的字节缓冲区。
     * @param position 此结构体在指定字节缓冲区中的位置。
     * @return <code>this</code>
     * @throws IllegalArgumentException 如果指定的 byteBuffer 的字节顺序与此结构体不同。
     * @throws UnsupportedOperationException 如果此结构体是一个内部结构体。
     * @see #byteOrder()
     */
    public final Struct setByteBuffer(ByteBuffer byteBuffer, int position) {
        if (byteBuffer.order() != byteOrder()) throw new IllegalArgumentException(
                "The byte order of the specified byte buffer"
                        + " is different from this struct byte order");
        if (_outer != null) throw new UnsupportedOperationException(
                "Inner struct byte buffer is inherited from outer");
        _byteBuffer = byteBuffer;
        _outerOffset = position;
        return this;
    }

    /**
     * 在字节缓冲区内设置此结构体的字节位置。
     *
     * @param position 此结构体在其字节缓冲区中的位置。
     * @return <code>this</code>
     * @throws UnsupportedOperationException 如果此结构体是一个内部结构体。
     */
    public final Struct setByteBufferPosition(int position) {
        return setByteBuffer(this.getByteBuffer(), position);
    }

    /**
     * 返回此结构体在其关联的 {@link #getByteBuffer 字节缓冲区} 中的绝对字节位置。
     *
     * @return 此结构体（可以是内部结构体）在字节缓冲区中的绝对位置。
     */
    public final int getByteBufferPosition() {
        return (_outer != null) ? _outer.getByteBufferPosition() + _outerOffset
                : _outerOffset;
    }

    /**
     * 从指定的输入流中读取此结构体（使用流 I/O 时的便捷方法）。
     * 为了获得更好的性能，建议使用块 I/O（例如 <code>java.nio.channels.*</code>）。
     * 当输入流并非所有数据都可用时，此方法表现得体。
     * 当输入流与 TCP 连接之类的事物关联时，数据不完整是非常常见的。
     * 在这些场景中，典型的用法模式是重复调用 read() 直到接收到整个消息。
     *  
     * @param in 正在从中读取的输入流。
     * @return 读取的字节数（通常是此结构体的 {@link #size() 大小}）。
     * @throws IOException if an I/O error occurs.
     */
    public int read(InputStream in) throws IOException {
        ByteBuffer buffer = getByteBuffer();
        int size = size();
        int remaining = size - buffer.position();
        if (remaining == 0) remaining = size;// at end so move to beginning
        int alreadyRead = size - remaining; // typically 0
        if (buffer.hasArray()) {
            int offset = buffer.arrayOffset() + getByteBufferPosition();
            int bytesRead = in.read(buffer.array(), offset + alreadyRead,
                    remaining);
            buffer.position(getByteBufferPosition() + alreadyRead + bytesRead
                    - offset);
            return bytesRead;
        } else {
            synchronized (buffer) {
                if (_bytes == null) {
                    _bytes = new byte[size()];
                }
                int bytesRead = in.read(_bytes, 0, remaining);
                buffer.position(getByteBufferPosition() + alreadyRead);
                buffer.put(_bytes, 0, bytesRead);
                return bytesRead;
            }
        }
    }

    /**
     * 将此结构体写入指定的输出流（使用流 I/O 时的便捷方法）。
     * 为了获得更好的性能，建议使用块 I/O（例如 <code>java.nio.channels.*</code>）。
     *
     * @param out 要写入的输出 stream。
     * @throws IOException 如果发生 I/O 错误。
     */
    public void write(OutputStream out) throws IOException {
        ByteBuffer buffer = getByteBuffer();
        if (buffer.hasArray()) {
            int offset = buffer.arrayOffset() + getByteBufferPosition();
            out.write(buffer.array(), offset, size());
        } else {
            synchronized (buffer) {
                if (_bytes == null) {
                    _bytes = new byte[size()];
                }
                buffer.position(getByteBufferPosition());
                buffer.get(_bytes);
                out.write(_bytes);
            }
        }
    }

    /**
     * 以构成字节（十六进制）的形式返回此结构体的 <code>String</code> 表示。例如：[code]
     *     public static class Student extends Struct {
     *         Utf8String name  = new Utf8String(16);
     *         Unsigned16 year  = new Unsigned16();
     *         Float32    grade = new Float32();
     *     }
     *     Student student = new Student();
     *     student.name.set("John Doe");
     *     student.year.set(2003);
     *     student.grade.set(12.5f);
     *     System.out.println(student);
     * <p>
     *     4A 6F 68 6E 20 44 6F 65 00 00 00 00 00 00 00 00
     *     07 D3 00 00 41 48 00 00[/code]
     *
     * @return 此结构体字节内容的十六进制表示。
     */
    @Override
    public String toString() {
        StringBuilder tmp = new StringBuilder();
        final int size = size();
        final ByteBuffer buffer = getByteBuffer();
        final int start = getByteBufferPosition();
        for (int i = 0; i < size; i++) {
            int b = buffer.get(start + i) & 0xFF;
            tmp.append(HEXA[b >> 4]);
            tmp.append(HEXA[b & 0xF]);
            tmp.append(((i & 0xF) == 0xF) ? '\n' : ' ');
        }
        return tmp.toString();
    }

    private static final char[] HEXA = { '0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    // CONFIGURATION //

    /**
     * 指示此结构体的成员是否映射到内存中的同一位置（默认为 <code>false</code>）。
     * 此方法对于使用新成员类型扩展 {@link Struct} 以便从这些新结构体创建联合（union）的应用程序很有用。
     * 例如：[code]
     * public abstract class FortranStruct extends Struct {
     *     public class FortranString extends Member {...}
     *     protected FortranString[] array(FortranString[] array, int stringLength) { ... }
     * }
     * public abstract class FortranUnion extends FortranStruct {
     *     // 继承新的成员和方法。
     *     public final isUnion() {
     *         return true;
     *     }
     * }[/code]
     *
     * @return 如果此结构体的成员映射到内存中的同一位置，则返回 <code>true</code>；
     *         否则返回 <code>false</code>。
     * @see Union
     */
    public boolean isUnion() {
        return false;
    }

    /**
     * 返回此结构体的字节顺序（可配置）。
     * 字节顺序由内部结构体继承。子类可以通过覆盖此方法来更改字节顺序。
     * 例如：[code]
     * public class TopStruct extends Struct {
     *     ... // 成员初始化。
     *     public ByteOrder byteOrder() {
     *         // TopStruct 及其内部结构体使用硬件字节顺序。
     *         return ByteOrder.nativeOrder();
     *    }
     * }}[/code]
     *
     * @return 读取/写入多字节值时的字节顺序
     *         （默认：网络字节顺序，<code>BIG_ENDIAN</code>）。
     */
    public ByteOrder byteOrder() {
        return (_outer != null) ? _outer.byteOrder() : ByteOrder.BIG_ENDIAN;
    }

    /**
     * 指示此结构体是否是紧凑的（可配置）。
     * 默认情况下，结构体的 {@link Member 成员} 按成员基础类型对应的边界对齐；
     * 如有必要，会进行填充。此指令<b>不会</b>被内部结构体继承。
     * 子类可以通过覆盖此方法来更改紧凑指令。
     * 例如：[code]
     * public class MyStruct extends Struct {
     *     ... // 成员初始化。
     *     public boolean isPacked() {
     *         return true; // MyStruct 是紧凑的。
     *     }
     * }}[/code]
     *
     * @return 如果忽略字大小要求，则返回 <code>true</code>。
     *         否则返回 <code>false</code>（默认）。
     */
    public boolean isPacked() {
        return false;
    }

    /**
     * 将指定的结构体定义为此结构体的内部结构体。
     *
     * @param <S> 内部结构体的类型
     * @param struct 内部结构体。
     * @return 指定的结构体。
     * @throws IllegalArgumentException 如果指定的结构体已经是内部结构体。
     */
    protected <S extends Struct> S inner(S struct) {
        if (struct._outer != null) throw new IllegalArgumentException(
                "struct: Already an inner struct");
        Member inner = new Member(struct.size() << 3, struct._alignment); // Update indexes.
        struct._outer = this;
        struct._outerOffset = inner.offset();
        return struct;
    }

    /**
     * 将指定的结构体数组定义为内部结构体。
     * 如有必要，将使用结构体组件的默认构造函数（必须为 public）填充数组。
     *
     * @param <S> 结构体数组的类型
     * @param structs 结构体数组。
     * @return 指定的结构体数组。
     * @throws IllegalArgumentException 如果指定的数组包含内部结构体。
     */
    protected <S extends Struct> S[] array(S[] structs) {
        Class<?> structClass = null;
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _index = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (int i = 0; i < structs.length;) {
            S struct = structs[i];
            if (struct == null) {
                try {
                    if (structClass == null) {
                        String arrayName = structs.getClass().getName();
                        String structName = arrayName.substring(2,
                                arrayName.length() - 1);
                        structClass = Class.forName(structName);
                    }
                    struct = (S) structClass.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to instantiate struct component: " + structClass, e);
                }
            }
            structs[i++] = inner(struct);
        }
        _resetIndex = resetIndexSaved;
        return structs;
    }

    /**
     * 将指定的二维结构体数组定义为内部结构体。
     * 如有必要，将使用结构体组件的默认构造函数（必须为 public）填充数组。
     *
     * @param <S> 结构体数组的类型
     * @param structs 二维结构体数组。
     * @return 指定的结构体数组。
     * @throws IllegalArgumentException 如果指定的数组包含内部结构体。
     */
    protected <S extends Struct> S[][] array(S[][] structs) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _index = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (S[] struct : structs) {
            array(struct);
        }
        _resetIndex = resetIndexSaved;
        return structs;
    }

    /**
     * 将指定的三维结构体数组定义为内部结构体。
     * 如有必要，将使用结构体组件的默认构造函数（必须为 public）填充数组。
     *
     * @param <S> 结构体数组的类型
     * @param structs 三维结构体数组。
     * @return 指定的结构体数组。
     * @throws IllegalArgumentException 如果指定的数组包含内部结构体。
     */
    protected <S extends Struct> S[][][] array(S[][][] structs) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _index = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (S[][] struct : structs) {
            array(struct);
        }
        _resetIndex = resetIndexSaved;
        return structs;
    }

    /**
     * 定义指定的数组成员。对于预定义成员，当数组为空时会自动填充；
     * 自定义成员应使用字面量（已填充）数组。
     *
     * @param <M> 数组成员的类型
     * @param  arrayMember 数组成员。
     * @return 指定的数组成员。
     * @throws UnsupportedOperationException 如果指定的数组为空且成员类型未知。
     */
    protected <M extends Member> M[] array(M[] arrayMember) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _index = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        if (BOOL.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Bool();
            }
        } else if (SIGNED_8.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Signed8();
            }
        } else if (UNSIGNED_8.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Unsigned8();
            }
        } else if (SIGNED_16.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Signed16();
            }
        } else if (UNSIGNED_16.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Unsigned16();
            }
        } else if (SIGNED_32.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Signed32();
            }
        } else if (UNSIGNED_32.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Unsigned32();
            }
        } else if (SIGNED_64.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Signed64();
            }
        } else if (FLOAT_32.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Float32();
            }
        } else if (FLOAT_64.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (M) this.new Float64();
            }
        } else {
            throw new UnsupportedOperationException(
                    "Cannot create member elements, the arrayMember should "
                            + "contain the member instances instead of null");
        }
        _resetIndex = resetIndexSaved;
        return arrayMember;
    }

    private static final Class<? extends Bool[]> BOOL = Bool[].class;
    private static final Class<? extends Signed8[]> SIGNED_8 = Signed8[].class;
    private static final Class<? extends Unsigned8[]> UNSIGNED_8 = Unsigned8[].class;
    private static final Class<? extends Signed16[]> SIGNED_16 = Signed16[].class;
    private static final Class<? extends Unsigned16[]> UNSIGNED_16 = Unsigned16[].class;
    private static final Class<? extends Signed32[]> SIGNED_32 = Signed32[].class;
    private static final Class<? extends Unsigned32[]> UNSIGNED_32 = Unsigned32[].class;
    private static final Class<? extends Signed64[]> SIGNED_64 = Signed64[].class;
    private static final Class<? extends Float32[]> FLOAT_32 = Float32[].class;
    private static final Class<? extends Float64[]> FLOAT_64 = Float64[].class;

    /**
     * 定义指定的二维数组成员。对于预定义成员，当数组为空时会自动填充；
     * 自定义成员应使用字面量（已填充）数组。
     *
     * @param <M> 数组成员的类型
     * @param  arrayMember 二维数组成员。
     * @return 指定的数组成员。
     * @throws UnsupportedOperationException 如果指定的数组为空且成员类型未知。
     */
    protected <M extends Member> M[][] array(M[][] arrayMember) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _index = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (M[] ms : arrayMember) {
            array(ms);
        }
        _resetIndex = resetIndexSaved;
        return arrayMember;
    }

    /**
     * 定义指定的三维数组成员。对于预定义成员，当数组为空时会自动填充；
     * 自定义成员应使用字面量（已填充）数组。
     * 
     * @param <M> 数组成员的类型
     * @param  arrayMember 三维数组成员。
     * @return 指定的数组成员。
     * @throws UnsupportedOperationException 如果指定的数组为空且成员类型未知。
     */
    protected <M extends Member> M[][][] array(M[][][] arrayMember) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _index = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (M[][] ms : arrayMember) {
            array(ms);
        }
        _resetIndex = resetIndexSaved;
        return arrayMember;
    }

    /**
     * 定义指定的 UTF-8 字符串数组，所有字符串都具有指定的长度（便捷方法）。
     *
     * @param  array 字符串数组。
     * @param stringLength 字符串元素的长度。
     * @return 指定的字符串数组。
     */
    protected UTF8String[] array(UTF8String[] array, int stringLength) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _index = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (int i = 0; i < array.length; i++) {
            array[i] = new UTF8String(stringLength);
        }
        _resetIndex = resetIndexSaved;
        return array;
    }

    /**
     * 以 long（带符号）整数值的形式从此 Struct 中读取指定的位。
     *
     * @param  bitOffset Struct 中的位起始位置。
     * @param  bitSize 位数。
     * @return 读取为带符号 long 的指定位。
     * @throws IllegalArgumentException 如果
     *         { {@code (bitOffset}  + bitSize - 1) / 8 >= this.size() }
     */
    public long readBits(int bitOffset, int bitSize) {
        if ((bitOffset + bitSize - 1) >> 3 >= this.size()) throw new IllegalArgumentException(
                "Attempt to read outside the Struct");
        int offset = bitOffset >> 3;
        int bitStart = bitOffset - (offset << 3);
        bitStart = (byteOrder() == ByteOrder.BIG_ENDIAN) ? bitStart : 64
                - bitSize - bitStart;
        int index = getByteBufferPosition() + offset;
        long value = readByteBufferLong(index);
        value <<= bitStart; // Clears preceding bits.
        value >>= (64 - bitSize); // Signed shift.
        return value;
    }

    private long readByteBufferLong(int index) {
        ByteBuffer byteBuffer = getByteBuffer();
        if (index + 8 < byteBuffer.limit()) return byteBuffer.getLong(index);
        // Else possible buffer overflow.
        if (byteBuffer.order() == ByteOrder.LITTLE_ENDIAN) {
            return (readByte(index, byteBuffer) & 0xff)
                    + ((readByte(++index, byteBuffer) & 0xff) << 8)
                    + ((readByte(++index, byteBuffer) & 0xff) << 16)
                    + ((readByte(++index, byteBuffer) & 0xffL) << 24)
                    + ((readByte(++index, byteBuffer) & 0xffL) << 32)
                    + ((readByte(++index, byteBuffer) & 0xffL) << 40)
                    + ((readByte(++index, byteBuffer) & 0xffL) << 48)
                    + ((readByte(++index, byteBuffer) & 0xffL) << 56);
        } else {
            return (((long) readByte(index, byteBuffer)) << 56)
                    + ((readByte(++index, byteBuffer) & 0xffL) << 48)
                    + ((readByte(++index, byteBuffer) & 0xffL) << 40)
                    + ((readByte(++index, byteBuffer) & 0xffL) << 32)
                    + ((readByte(++index, byteBuffer) & 0xffL) << 24)
                    + ((readByte(++index, byteBuffer) & 0xff) << 16)
                    + ((readByte(++index, byteBuffer) & 0xff) << 8)
                    + (readByte(++index, byteBuffer) & 0xffL);
        }
    }

    private static byte readByte(int index, ByteBuffer byteBuffer) {
        return (index < byteBuffer.limit()) ? byteBuffer.get(index) : 0;
    }

    /**
     * 将指定的位写入此 Struct。
     *
     * @param  value 带符号 long 形式的位值。
     * @param  bitOffset Struct 中的位起始位置。
     * @param  bitSize 位数。
     * @throws IllegalArgumentException 如果
     *         { {@code (bitOffset}  + bitSize - 1) / 8 >= this.size() }
     */
    public void writeBits(long value, int bitOffset, int bitSize) {
        if ((bitOffset + bitSize - 1) >> 3 >= this.size()) throw new IllegalArgumentException(
                "Attempt to write outside the Struct");
        int offset = bitOffset >> 3;
        int bitStart = (byteOrder() == ByteOrder.BIG_ENDIAN) ? bitOffset
                - (offset << 3) : 64 - bitSize - (bitOffset - (offset << 3));
        long mask = -1L;
        mask <<= bitStart; // Clears preceding bits
        mask >>>= (64 - bitSize); // Unsigned shift.
        mask <<= 64 - bitSize - bitStart;
        value <<= (64 - bitSize - bitStart);
        value &= mask; // Protects against out of range values.
        int index = getByteBufferPosition() + offset;
        long oldValue = readByteBufferLong(index);
        long resetValue = oldValue & (~mask);
        long newValue = resetValue | value;
        writeByteBufferLong(index, newValue);
    }

    private void writeByteBufferLong(int index, long value) {
        ByteBuffer byteBuffer = getByteBuffer();
        if (index + 8 < byteBuffer.limit()) {
            byteBuffer.putLong(index, value);
            return;
        }
        // Else possible buffer overflow.
        if (byteBuffer.order() == ByteOrder.LITTLE_ENDIAN) {
            writeByte(index, byteBuffer, (byte) value);
            writeByte(++index, byteBuffer, (byte) (value >> 8));
            writeByte(++index, byteBuffer, (byte) (value >> 16));
            writeByte(++index, byteBuffer, (byte) (value >> 24));
            writeByte(++index, byteBuffer, (byte) (value >> 32));
            writeByte(++index, byteBuffer, (byte) (value >> 40));
            writeByte(++index, byteBuffer, (byte) (value >> 48));
            writeByte(++index, byteBuffer, (byte) (value >> 56));
        } else {
            writeByte(index, byteBuffer, (byte) (value >> 56));
            writeByte(++index, byteBuffer, (byte) (value >> 48));
            writeByte(++index, byteBuffer, (byte) (value >> 40));
            writeByte(++index, byteBuffer, (byte) (value >> 32));
            writeByte(++index, byteBuffer, (byte) (value >> 24));
            writeByte(++index, byteBuffer, (byte) (value >> 16));
            writeByte(++index, byteBuffer, (byte) (value >> 8));
            writeByte(++index, byteBuffer, (byte) value);
        }
    }

    private static void writeByte(int index, ByteBuffer byteBuffer, byte value) {
        if (index < byteBuffer.limit()) {
            byteBuffer.put(index, value);
        }
    }

    // MEMBERS //

    /**
     * 此内部类代表所有 {@link Struct} 成员的基类。
     * 它允许应用程序定义额外的成员类型。
     * 例如：[code]
     *    public class MyStruct extends Struct {
     *        BitSet bits = new BitSet(256);
     *        ...
     *        public BitSet extends Member {
     *            public BitSet(int nbrBits) {
     *                super(nbrBits, 0); // 直接位访问。
     *            }
     *            public boolean get(int i) { ... }
     *            public void set(int i, boolean value) { ...}
     *        }
     *    }[/code]
     */
    protected class Member {

        /**
         * 持有此成员在其结构体中的相对偏移量（以字节为单位）。
         */
        private final int _offset;
        /**
         * 持有此成员相对于其结构体偏移量的相对位偏移量。
         */
        private final int _bitIndex;
        /**
         * 持有此成员的位长度。
         */
        private final int _bitLength;

        /**
         * 自定义成员类型的基类构造函数。
         * <p>
         * 字大小可以为零，在这种情况下成员的 {@link #offset} 不会改变，
         * 仅 {@link #bitIndex} 会增加。
         *
         * @param  bitLength 位数，或为 <code>0</code> 以强制下一个成员处于下一个字边界。
         * @param  wordSize 访问此成员数据时使用的以字节为单位的字大小，
         *         如果是在位级别访问数据则为 <code>0</code>。
         */
        protected Member(int bitLength, int wordSize) {
            _bitLength = bitLength;

            // Resets index if union.
            if (_resetIndex) {
                _index = 0;
            }

            // Check if we can merge bitfields (always true if no word boundary).
            if ((wordSize == 0)
                    || ((bitLength != 0) && (wordSize == _wordSize) && ((_bitsUsed + bitLength) <= (wordSize << 3)))) {

                _offset = _index - _wordSize;
                _bitIndex = _bitsUsed;
                _bitsUsed += bitLength;

                // Straddling word boundary only possible if (wordSize == 0)
                while (_bitsUsed > (_wordSize << 3)) {
                    _index++;
                    _wordSize++;
                    _length = Math.max(_length, _index);
                }
                return; // Bit field merge done.
            }

            // Check alignment.
            if (!isPacked()) {

                // Updates struct's alignment constraint, based on largest word size.
                if ((_alignment < wordSize)) {
                    _alignment = wordSize;
                }

                // Adds padding if misaligned.
                int misaligned = _index % wordSize;
                if (misaligned != 0) {
                    _index += wordSize - misaligned;
                }
            }

            // Sets member indices.
            _offset = _index;
            _bitIndex = 0;

            // Update struct indices.
            _index += Math.max(wordSize, (bitLength + 7) >> 3);
            _wordSize = wordSize;
            _bitsUsed = bitLength;
            _length = Math.max(_length, _index);
            // size and index may differ because of {@link Union}
        }

        /**
         * 返回外部 {@link Struct 结构体} 容器。
         *
         * @return 外部结构体。
         */
        public final Struct struct() {
            return Struct.this;
        }

        /**
         * 返回此成员在其结构体中的字节偏移量。
         * 等同于 C/C++ <code>offsetof(struct(), this)</code>
         *
         * @return 成员在 Struct 中的偏移量。
         */
        public final int offset() {
            return _offset;
        }

        /**
         * 持有此成员的位偏移量（如果有）。
         * 位数据的实际位置取决于端序（endianess）和字大小。
         * 
         * @return 表示位索引的整数
         */
        public final int bitIndex() {
            return _bitIndex;
        }

        /**
         * 返回此成员中的位数。如果此成员用于强制下一个成员到下一个字边界，则可以为零。
         *
         * @return 成员中的位数。
         */
        public final int bitLength() {
            return _bitLength;
        }

        // Returns the member int value.
        final int get(int wordSize, int word) {
            final int shift = (byteOrder() == ByteOrder.BIG_ENDIAN) ? (wordSize << 3)
                    - bitIndex() - bitLength()
                    : bitIndex();
            word >>= shift;
            int mask = 0xFFFFFFFF >>> (32 - bitLength());
            return word & mask;
        }

        // Sets the member int value.
        final int set(int value, int wordSize, int word) {
            final int shift = (byteOrder() == ByteOrder.BIG_ENDIAN) ? (wordSize << 3)
                    - bitIndex() - bitLength()
                    : bitIndex();
            int mask = 0xFFFFFFFF >>> (32 - bitLength());
            mask <<= shift;
            value <<= shift;
            return (word & ~mask) | (value & mask);
        }

        // Returns the member-long value.
        final long get(int wordSize, long word) {
            final int shift = (byteOrder() == ByteOrder.BIG_ENDIAN) ? (wordSize << 3)
                    - bitIndex() - bitLength()
                    : bitIndex();
            word >>= shift;
            long mask = 0xFFFFFFFFFFFFFFFFL >>> (64 - bitLength());
            return word & mask;
        }

        // Sets the member-long value.
        final long set(long value, int wordSize, long word) {
            final int shift = (byteOrder() == ByteOrder.BIG_ENDIAN) ? (wordSize << 3)
                    - bitIndex() - bitLength()
                    : bitIndex();
            long mask = 0xFFFFFFFFFFFFFFFFL >>> (64 - bitLength());
            mask <<= shift;
            value <<= shift;
            return (word & ~mask) | (value & mask);
        }
    }

    // PREDEFINED FIELDS //

    /**
     * 此类表示一个 UTF-8 字符串，以 null 结尾（为了 C/C++ 兼容性）
     */
    public class UTF8String extends Member {

        private final UTF8ByteBufferWriter _writer = new UTF8ByteBufferWriter();
        private final UTF8ByteBufferReader _reader = new UTF8ByteBufferReader();
        private final int _length;

        public UTF8String(int length) {
            super(length << 3, 1);
            _length = length; // Takes into account 0 terminator.
        }

        public void set(String string) {
            final ByteBuffer buffer = getByteBuffer();
            synchronized (buffer) {
                try {
                    int index = getByteBufferPosition() + offset();
                    buffer.position(index);
                    _writer.setOutput(buffer);
                    if (string.length() < _length) {
                        _writer.write(string);
                        _writer.write(0); // Marks end of string.
                    } else if (string.length() > _length) { // Truncates.
                        _writer.write(string.substring(0, _length));
                    } else { // Exact same length.
                        _writer.write(string);
                    }
                } catch (IOException e) { // Should never happen.
                    throw new Error(e.getMessage());
                } finally {
                    _writer.reset();
                }
            }
        }

        public String get() {
            final ByteBuffer buffer = getByteBuffer();
            synchronized (buffer) {
                StringBuilder tmp = new StringBuilder();
                try {
                    int index = getByteBufferPosition() + offset();
                    buffer.position(index);
                    _reader.setInput(buffer);
                    for (int i = 0; i < _length; i++) {
                        char c = (char) _reader.read();
                        if (c == 0) { // Null terminator.
                            return tmp.toString();
                        } else {
                            tmp.append(c);
                        }
                    }
                    return tmp.toString();
                } catch (IOException e) { // Should never happen.
                    throw new Error(e.getMessage());
                } finally {
                    _reader.reset();
                }
            }
        }

        public String toString() {
            return this.get();
        }
    }

    /**
     * 此类表示 8 位布尔值，<code>true</code> 由 <code>1</code> 表示，
     * <code>false</code> 由 <code>0</code> 表示。
     */
    public class Bool extends Member {

        public Bool() {
            super(8, 1);
        }

        public Bool(int nbrOfBits) {
            super(nbrOfBits, 1);
        }

        public boolean get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().get(index);
            word = (bitLength() == 8) ? word : get(1, word);
            return word != 0;
        }

        public void set(boolean value) {
            final int index = getByteBufferPosition() + offset();
            if (bitLength() == 8) {
                getByteBuffer().put(index, (byte) (value ? -1 : 0));
            } else {
                getByteBuffer().put(
                        index,
                        (byte) set(value ? -1 : 0, 1, getByteBuffer()
                                .get(index)));
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 8 位有符号整数。
     */
    public class Signed8 extends Member {

        public Signed8() {
            super(8, 1);
        }

        public Signed8(int nbrOfBits) {
            super(nbrOfBits, 1);
        }

        public byte get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().get(index);
            return (byte) ((bitLength() == 8) ? word : get(1, word));
        }

        public void set(byte value) {
            final int index = getByteBufferPosition() + offset();
            if (bitLength() == 8) {
                getByteBuffer().put(index, value);
            } else {
                getByteBuffer().put(index,
                        (byte) set(value, 1, getByteBuffer().get(index)));
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 8 位无符号整数。
     */
    public class Unsigned8 extends Member {

        public Unsigned8() {
            super(8, 1);
        }

        public Unsigned8(int nbrOfBits) {
            super(nbrOfBits, 1);
        }

        public short get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().get(index);
            return (short) (0xFF & ((bitLength() == 8) ? word : get(1, word)));
        }

        public void set(short value) {
            final int index = getByteBufferPosition() + offset();
            if (bitLength() == 8) {
                getByteBuffer().put(index, (byte) value);
            } else {
                getByteBuffer().put(index,
                        (byte) set(value, 1, getByteBuffer().get(index)));
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 16 位有符号整数。
     */
    public class Signed16 extends Member {

        public Signed16() {
            super(16, 2);
        }

        public Signed16(int nbrOfBits) {
            super(nbrOfBits, 2);
        }

        public short get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().getShort(index);
            return (short) ((bitLength() == 16) ? word : get(2, word));
        }

        public void set(short value) {
            final int index = getByteBufferPosition() + offset();
            if (bitLength() == 16) {
                getByteBuffer().putShort(index, value);
            } else {
                getByteBuffer().putShort(index,
                        (short) set(value, 2, getByteBuffer().getShort(index)));
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 16 位无符号整数。
     */
    public class Unsigned16 extends Member {

        public Unsigned16() {
            super(16, 2);
        }

        public Unsigned16(int nbrOfBits) {
            super(nbrOfBits, 2);
        }

        public int get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().getShort(index);
            return 0xFFFF & ((bitLength() == 16) ? word : get(2, word));
        }

        public void set(int value) {
            final int index = getByteBufferPosition() + offset();
            if (bitLength() == 16) {
                getByteBuffer().putShort(index, (short) value);
            } else {
                getByteBuffer().putShort(index,
                        (short) set(value, 2, getByteBuffer().getShort(index)));
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 32 位有符号整数。
     */
    public class Signed32 extends Member {

        public Signed32() {
            super(32, 4);
        }

        public Signed32(int nbrOfBits) {
            super(nbrOfBits, 4);
        }

        public int get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().getInt(index);
            return (bitLength() == 32) ? word : get(4, word);
        }

        public void set(int value) {
            final int index = getByteBufferPosition() + offset();
            if (bitLength() == 32) {
                getByteBuffer().putInt(index, value);
            } else {
                getByteBuffer().putInt(index,
                        set(value, 4, getByteBuffer().getInt(index)));
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 32 位无符号整数。
     */
    public class Unsigned32 extends Member {

        public Unsigned32() {
            super(32, 4);
        }

        public Unsigned32(int nbrOfBits) {
            super(nbrOfBits, 4);
        }

        public long get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().getInt(index);
            return 0xFFFFFFFFL & ((bitLength() == 32) ? word : get(4, word));
        }

        public void set(long value) {
            final int index = getByteBufferPosition() + offset();
            if (bitLength() == 32) {
                getByteBuffer().putInt(index, (int) value);
            } else {
                getByteBuffer().putInt(index,
                        set((int) value, 4, getByteBuffer().getInt(index)));
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 64 位有符号整数。
     */
    public class Signed64 extends Member {

        public Signed64() {
            super(64, 8);
        }

        public Signed64(int nbrOfBits) {
            super(nbrOfBits, 8);
        }

        public long get() {
            final int index = getByteBufferPosition() + offset();
            long word = getByteBuffer().getLong(index);
            return (bitLength() == 64) ? word : get(8, word);
        }

        public void set(long value) {
            final int index = getByteBufferPosition() + offset();
            if (bitLength() == 64) {
                getByteBuffer().putLong(index, value);
            } else {
                getByteBuffer().putLong(index,
                        set(value, 8, getByteBuffer().getLong(index)));
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示任意大小的（无符号）位域，没有字大小限制（它们可以跨越字边界）。
     */
    public class BitField extends Member {

        public BitField(int nbrOfBits) {
            super(nbrOfBits, 0);
        }

        public long longValue() {
            long signedValue = readBits(bitIndex() + (offset() << 3),
                    bitLength());
            return ~(-1L << bitLength()) & signedValue;
        }

        public int intValue() {
            return (int) longValue();
        }

        public short shortValue() {
            return (short) longValue();
        }

        public byte byteValue() {
            return (byte) longValue();
        }

        public void set(long value) {
            writeBits(value, bitIndex() + (offset() << 3), bitLength());
        }

        public String toString() {
            return String.valueOf(longValue());
        }
    }

    /**
     * 此类表示 32 位浮点数（C/C++/Java <code>float</code>）。
     */
    public class Float32 extends Member {

        public Float32() {
            super(32, 4);
        }

        public float get() {
            final int index = getByteBufferPosition() + offset();
            return getByteBuffer().getFloat(index);
        }

        public void set(float value) {
            final int index = getByteBufferPosition() + offset();
            getByteBuffer().putFloat(index, value);
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 64 位浮点数（C/C++/Java <code>double</code>）。
     */
    public class Float64 extends Member {

        public Float64() {
            super(64, 8);
        }

        public double get() {
            final int index = getByteBufferPosition() + offset();
            return getByteBuffer().getDouble(index);
        }

        public void set(double value) {
            final int index = getByteBufferPosition() + offset();
            getByteBuffer().putDouble(index, value);
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 8 位 {@link Enum}。
     */
    public class Enum8<T extends Enum<T>> extends Member {

        private final T[] _values;

        public Enum8(T[] values) {
            super(8, 1);
            _values = values;
        }

        public Enum8(T[] values, int nbrOfBits) {
            super(nbrOfBits, 1);
            _values = values;
        }

        public T get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().get(index);
            return _values[0xFF & get(1, word)];
        }

        public void set(T e) {
            int value = e.ordinal();
            if (_values[value] != e) throw new IllegalArgumentException(
                    "enum: "
                            + e
                            + ", ordinal value does not reflect enum values position");
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().get(index);
            getByteBuffer().put(index, (byte) set(value, 1, word));
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 16 位 {@link Enum}。
     */
    public class Enum16<T extends Enum<T>> extends Member {

        private final T[] _values;

        public Enum16(T[] values) {
            super(16, 2);
            _values = values;
        }

        public Enum16(T[] values, int nbrOfBits) {
            super(nbrOfBits, 2);
            _values = values;
        }

        public T get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().getShort(index);
            return _values[0xFFFF & get(2, word)];
        }

        public void set(T e) {
            int value = e.ordinal();
            if (_values[value] != e) throw new IllegalArgumentException(
                    "enum: "
                            + e
                            + ", ordinal value does not reflect enum values position");
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().getShort(index);
            getByteBuffer().putShort(index, (short) set(value, 2, word));
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 32 位 {@link Enum}。
     */
    public class Enum32<T extends Enum<T>> extends Member {

        private final T[] _values;

        public Enum32(T[] values) {
            super(32, 4);
            _values = values;
        }

        public Enum32(T[] values, int nbrOfBits) {
            super(nbrOfBits, 4);
            _values = values;
        }

        public T get() {
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().getInt(index);
            return _values[get(4, word)];
        }

        public void set(T e) {
            int value = e.ordinal();
            if (_values[value] != e) throw new IllegalArgumentException(
                    "enum: "
                            + e
                            + ", ordinal value does not reflect enum values position");
            final int index = getByteBufferPosition() + offset();
            int word = getByteBuffer().getInt(index);
            getByteBuffer().putInt(index, set(value, 4, word));
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * 此类表示 64 位 {@link Enum}。
     */
    public class Enum64<T extends Enum<T>> extends Member {

        private final T[] _values;

        public Enum64(T[] values) {
            super(64, 8);
            _values = values;
        }

        public Enum64(T[] values, int nbrOfBits) {
            super(nbrOfBits, 8);
            _values = values;
        }

        public T get() {
            final int index = getByteBufferPosition() + offset();
            long word = getByteBuffer().getLong(index);
            return _values[(int) get(8, word)];
        }

        public void set(T e) {
            long value = e.ordinal();
            if (_values[(int) value] != e) throw new IllegalArgumentException(
                    "enum: "
                            + e
                            + ", ordinal value does not reflect enum values position");
            final int index = getByteBufferPosition() + offset();
            long word = getByteBuffer().getLong(index);
            getByteBuffer().putLong(index, set(value, 8, word));
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }
}
