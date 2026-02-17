/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2012 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */
package org.javolution.io;

import java.io.CharConversionException;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;

/**
 * <p> 一个 UTF-8 <code>java.nio.ByteBuffer</code> 写入器（Writer）。</p>
 *
 * <p> 此写入器支持代理 <code>char</code> 对（表示 [U+10000. U+10FFFF] 范围内的字符）。
 *     它也可用于直接从字符的 Unicode（31 位）进行写入（参考 {@link #write(int)}）。</p>
 *
 * <p> 此类的实例可以重复用于不同的输出流，并且可以作为更高级别组件（例如序列化器）的一部分，
 *     以避免在目标输出更改时进行动态缓冲区分配。
 *     此外，由于此类的实例嵌入了自己的数据缓冲区，因此无需使用 <code>java.io.BufferedWriter</code> 进行包装。</p>
 * 
 * <p> 注意：此写入器是未同步的，并且始终生成格式良好的 UTF-8 序列。</p>
 *
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @version 2.0, December 9, 2004
 * @see     UTF8ByteBufferReader
 */
@SuppressWarnings("unused")
public final class UTF8ByteBufferWriter extends Writer {

    /**
     * 持有目标字节缓冲区。
     */
    private ByteBuffer _byteBuffer;

    /**
     * 默认构造函数。
     */
    public UTF8ByteBufferWriter() {}

    /**
	 * 在初始化时提供字节缓冲区的构造函数。
	 *
	 * @param byteBuffer 用于写入的字节缓冲区
	 */
	public UTF8ByteBufferWriter(final ByteBuffer byteBuffer) {
		_byteBuffer = byteBuffer;
	}
	
	private ByteBuffer getOutput(){
		return _byteBuffer;
	}
	
    /**
     * 设置用于写入的字节缓冲区，直到此写入器关闭。
     *
     * @param  byteBuffer 目标字节缓冲区。
     * @return 此 UTF-8 写入器。
     * @throws IllegalStateException 如果此写入器正在被重复使用，
     *         且尚未 {@link #close 关闭} 或 {@link #reset 重置}。
     */
    public UTF8ByteBufferWriter setOutput(ByteBuffer byteBuffer) {
        if (_byteBuffer != null)
            throw new IllegalStateException("Writer not closed or reset");
        _byteBuffer = byteBuffer;
        return this;
    }

    /**
     * 写入单个字符。此方法支持 16 位字符代理（surrogates）。
     *
     * @param  c 要写入的 <code>char</code> 字符（可能是代理项）。
     * @throws IOException 如果发生 I/O 错误。
     */
    public void write(char c) throws IOException {
    	if(_byteBuffer == null)
    		throw new IOException("Writer closed");
        if ((c < 0xd800) || (c > 0xdfff)) {
            write((int) c);
        } else if (c < 0xdc00) { // High surrogate.
            _highSurrogate = c;
        } else { // Low surrogate.
            int code = ((_highSurrogate - 0xd800) << 10) + (c - 0xdc00)
                    + 0x10000;
            write(code);
        }
    }

    private char _highSurrogate;

    /**
     * 写入给定其 31 位 Unicode 的字符。
     *
     * @param  code 要写入字符的 31 位 Unicode。
     * @throws IOException 如果发生 I/O 错误。
     */
    public void write(int code) throws IOException {
    	if(_byteBuffer == null)
    		throw new IOException("Writer closed");
        if ((code & 0xffffff80) == 0) {
            _byteBuffer.put((byte) code);
        } else { // Writes more than one byte.
            write2(code);
        }
    }

    private void write2(int c) throws IOException {
        if ((c & 0xfffff800) == 0) { // 2 bytes.
            _byteBuffer.put((byte) (0xc0 | (c >> 6)));
            _byteBuffer.put((byte) (0x80 | (c & 0x3f)));
        } else if ((c & 0xffff0000) == 0) { // 3 bytes.
            _byteBuffer.put((byte) (0xe0 | (c >> 12)));
            _byteBuffer.put((byte) (0x80 | ((c >> 6) & 0x3f)));
            _byteBuffer.put((byte) (0x80 | (c & 0x3f)));
        } else if ((c & 0xff200000) == 0) { // 4 bytes.
            _byteBuffer.put((byte) (0xf0 | (c >> 18)));
            _byteBuffer.put((byte) (0x80 | ((c >> 12) & 0x3f)));
            _byteBuffer.put((byte) (0x80 | ((c >> 6) & 0x3f)));
            _byteBuffer.put((byte) (0x80 | (c & 0x3f)));
        } else if ((c & 0xf4000000) == 0) { // 5 bytes.
            _byteBuffer.put((byte) (0xf8 | (c >> 24)));
            _byteBuffer.put((byte) (0x80 | ((c >> 18) & 0x3f)));
            _byteBuffer.put((byte) (0x80 | ((c >> 12) & 0x3f)));
            _byteBuffer.put((byte) (0x80 | ((c >> 6) & 0x3f)));
            _byteBuffer.put((byte) (0x80 | (c & 0x3f)));
        } else if ((c & 0x80000000) == 0) { // 6 bytes.
            _byteBuffer.put((byte) (0xfc | (c >> 30)));
            _byteBuffer.put((byte) (0x80 | ((c >> 24) & 0x3f)));
            _byteBuffer.put((byte) (0x80 | ((c >> 18) & 0x3f)));
            _byteBuffer.put((byte) (0x80 | ((c >> 12) & 0x3F)));
            _byteBuffer.put((byte) (0x80 | ((c >> 6) & 0x3F)));
            _byteBuffer.put((byte) (0x80 | (c & 0x3F)));
        } else {
            throw new CharConversionException("Illegal character U+"
                    + Integer.toHexString(c));
        }
    }

    /**
     * 写入字符数组的一部分。
     *
     * @param  cbuf 字符数组。
     * @param  off 开始写入字符的偏移量。
     * @param  len 要写入的字符数。
     * @throws IOException 如果发生 I/O 错误。
     */
    public void write(char cbuf[], int off, int len) throws IOException {
    	if(_byteBuffer == null)
    		throw new IOException("Writer closed");
        final int off_plus_len = off + len;
        for (int i = off; i < off_plus_len;) {
            char c = cbuf[i++];
            if (c < 0x80) {
                _byteBuffer.put((byte) c);
            } else {
                write(c);
            }
        }
    }

    /**
     * 写入字符串的一部分。
     *
     * @param  str 字符串。
     * @param  off 开始写入字符的偏移量。
     * @param  len 要写入的字符数。
     * @throws IOException 如果发生 I/O 错误
     */
    public void write(String str, int off, int len) throws IOException {
    	if(_byteBuffer == null)
    		throw new IOException("Writer closed");
        final int off_plus_len = off + len;
        for (int i = off; i < off_plus_len;) {
            char c = str.charAt(i++);
            if (c < 0x80) {
                _byteBuffer.put((byte) c);
            } else {
                write(c);
            }
        }
    }

    /**
     * 写入指定的字符序列。
     *
     * @param  csq 字符序列。
     * @throws IOException 如果发生 I/O 错误
     */
    public void write(CharSequence csq) throws IOException {
    	if(_byteBuffer == null)
    		throw new IOException("Writer closed");
        final int length = csq.length();
        for (int i = 0; i < length;) {
            char c = csq.charAt(i++);
            if (c < 0x80) {
                _byteBuffer.put((byte) c);
            } else {
                write(c);
            }
        }
    }

    /**
     * 刷新流（此方法没有任何效果，数据始终直接写入 <code>ByteBuffer</code>）。
     *
     * @throws IOException 如果发生 I/O 错误。
     */
    public void flush() throws IOException {
        if (_byteBuffer == null) { throw new IOException("Writer closed"); }
    }

    /**
     * 关闭并 {@link #reset 重置} 此写入器以供重复使用。
     *
     */
    public void close() {
        if (_byteBuffer != null) {
            reset();
        }
    }

    // Implements Reusable.
    public void reset() {
        _byteBuffer = null;
        _highSurrogate = 0;
    }

    /**
     * @deprecated Replaced by {@link #setOutput(ByteBuffer)}
     * @param byteBuffer ByteBuffer to write to
     * @return Reference to this UTF8ByteBufferWriter
     */
    public UTF8ByteBufferWriter setByteBuffer(ByteBuffer byteBuffer) {
        return this.setOutput(byteBuffer);
    }

}