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
import java.io.Reader;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * <p> 一个 UTF-8 <code>java.nio.ByteBuffer</code> 读取器（Reader）。
 *     </p>
 *
 * <p> 此读取器可用于对原生字节缓冲区（例如 <code>MappedByteBuffer</code>）进行高效解码、
 *     高性能消息传递（无中间缓冲区）等。</p>
 *     
 * <p> 此读取器支持代理 <code>char</code> 对（表示 [U+10000. U+10FFFF] 范围内的字符）。
 *     它也可用于直接读取 Unicode 字符（31 位）（参考 {@link #read()}）。</p>
 *
 * <p> 每次调用 <code>read()</code> 方法都可能导致从底层字节缓冲区读取一个或多个字节。
 *     当字节缓冲区的位置（position）和限制（limit）重合时，表示到达流的末尾。</p>
 *
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @version 2.0, December 9, 2004
 * @see     UTF8ByteBufferWriter
 */
@SuppressWarnings("unused")
public final class UTF8ByteBufferReader extends Reader {

    /**
     * 持有源字节缓冲区。
     */
    private ByteBuffer _byteBuffer;

    /**
     * 默认构造函数。
     */
    public UTF8ByteBufferReader() {}
    
    /**
     * 初始化为从指定字节缓冲区读取的构造函数。
     * 
     * @param byteBuffer 要从中读取的 ByteBuffer
     */
    public UTF8ByteBufferReader(ByteBuffer byteBuffer) {
    	_byteBuffer = byteBuffer;
    }

    /**
     * 设置 <code>ByteBuffer</code>，用于从当前缓冲区位置开始读取可用字节。
     *
     * @param  byteBuffer 源 <code>ByteBuffer</code>。
     * @return 此 UTF-8 读取器。
     * @throws IllegalStateException 如果此读取器正在被重复使用，
     *         且尚未 {@link #close 关闭} 或 {@link #reset 重置}。
     */
    public UTF8ByteBufferReader setInput(ByteBuffer byteBuffer) {
        if (_byteBuffer != null)
            throw new IllegalStateException("Reader not closed or reset");
        _byteBuffer = byteBuffer;
        return this;
    }

    /**
     * 指示此流是否已准备好被读取。
     *
     * @return 如果字节缓冲区还有剩余字节可读，则返回 <code>true</code>；
     *         否则返回 <code>false</code>。
     * @throws  IOException 如果发生 I/O 错误。
     */
    public boolean ready() throws IOException {
        if (_byteBuffer != null) {
            return _byteBuffer.hasRemaining();
        } else {
            throw new IOException("Reader closed");
        }
    }

    /**
     * 关闭并 {@link #reset 重置} 此读取器以供重复使用。
     *
     */
    public void close() {
        if (_byteBuffer != null) {
            reset();
        }
    }

    /**
     * 读取单个字符。此方法不会阻塞，如果已达到缓冲区的限制，则返回 <code>-1</code>。
     *
     * @return 读取字符的 31 位 Unicode，如果没有更多可读字节，则返回 -1。
     * @throws IOException 如果发生 I/O 错误（例如，读取到不完整的字符序列）。
     */
    public int read() throws IOException {
        if (_byteBuffer != null) {
            if (_byteBuffer.hasRemaining()) {
                byte b = _byteBuffer.get();
                return (b >= 0) ? b : read2(b);
            } else {
                return -1;
            }
        } else {
            throw new IOException("Reader closed");
        }
    }

    // Reads one full character, throws CharConversionException if limit reached.
    private int read2(byte b) throws IOException {
        try {
            // Decodes UTF-8.
            if ((b >= 0) && (_moreBytes == 0)) {
                // 0xxxxxxx
                return b;
            } else if (((b & 0xc0) == 0x80) && (_moreBytes != 0)) {
                // 10xxxxxx (continuation byte)
                _code = (_code << 6) | (b & 0x3f); // Adds 6 bits to code.
                if (--_moreBytes == 0) {
                    return _code;
                } else {
                    return read2(_byteBuffer.get());
                }
            } else if (((b & 0xe0) == 0xc0) && (_moreBytes == 0)) {
                // 110xxxxx
                _code = b & 0x1f;
                _moreBytes = 1;
                return read2(_byteBuffer.get());
            } else if (((b & 0xf0) == 0xe0) && (_moreBytes == 0)) {
                // 1110xxxx
                _code = b & 0x0f;
                _moreBytes = 2;
                return read2(_byteBuffer.get());
            } else if (((b & 0xf8) == 0xf0) && (_moreBytes == 0)) {
                // 11110xxx
                _code = b & 0x07;
                _moreBytes = 3;
                return read2(_byteBuffer.get());
            } else if (((b & 0xfc) == 0xf8) && (_moreBytes == 0)) {
                // 111110xx
                _code = b & 0x03;
                _moreBytes = 4;
                return read2(_byteBuffer.get());
            } else if (((b & 0xfe) == 0xfc) && (_moreBytes == 0)) {
                // 1111110x
                _code = b & 0x01;
                _moreBytes = 5;
                return read2(_byteBuffer.get());
            } else {
                throw new CharConversionException("Invalid UTF-8 Encoding");
            }
        } catch (BufferUnderflowException e) {
            throw new CharConversionException("Incomplete Sequence");
        }
    }

    private int _code;

    private int _moreBytes;

    /**
     * 将字符读取到数组的一部分中。此方法不会阻塞。
     *
     * <p> 注意：U+10000 和 U+10FFFF 之间的字符由代理对（两个 <code>char</code>）表示。</p>
     *
     * @param  cbuf 目标缓冲区。
     * @param  off 开始存储字符的偏移量。
     * @param  len 要读取的最大字符数
     * @return 读取的字符数，如果没有更多字节剩余，则返回 -1。
     * @throws IOException 如果发生 I/O 错误。
     */
    public int read(char cbuf[], int off, int len) throws IOException {
        if (_byteBuffer == null)
            throw new IOException("Reader closed");
        final int off_plus_len = off + len;
        int remaining = _byteBuffer.remaining();
        if (remaining <= 0)
            return -1;
        for (int i = off; i < off_plus_len;) {
            if (remaining-- > 0) {
                byte b = _byteBuffer.get();
                if (b >= 0) {
                    cbuf[i++] = (char) b; // Most common case.
                } else {
                    if (i < off_plus_len - 1) { // Up to two 'char' can be read.
                        int code = read2(b);
                        remaining = _byteBuffer.remaining(); // Recalculates.
                        if (code < 0x10000) {
                            cbuf[i++] = (char) code;
                        } else if (code <= 0x10ffff) { // Surrogates.
                            cbuf[i++] = (char) (((code - 0x10000) >> 10) + 0xd800);
                            cbuf[i++] = (char) (((code - 0x10000) & 0x3ff) + 0xdc00);
                        } else {
                            throw new CharConversionException(
                                    "Cannot convert U+"
                                            + Integer.toHexString(code)
                                            + " to char (code greater than U+10FFFF)");
                        }
                    } else { // Not enough space in destination (go back).
                        _byteBuffer.position(_byteBuffer.position() - 1);
                        remaining++;
                        return i - off;
                    }
                }
            } else {
                return i - off;
            }
        }
        return len;
    }

    /**
     * 将字符读取到指定的可追加对象（appendable）中。此方法不会阻塞。
     *
     * <p> 注意：U+10000 和 U+10FFFF 之间的字符由代理对（两个 <code>char</code>）表示。</p>
     *
     * @param  dest 目标缓冲区。
     * @throws IOException 如果发生 I/O 错误。
     */
    public void read(Appendable dest) throws IOException {
        if (_byteBuffer == null)
            throw new IOException("Reader closed");
        while (_byteBuffer.hasRemaining()) {
            byte b = _byteBuffer.get();
            if (b >= 0) {
                dest.append((char) b); // Most common case.
            } else {
                int code = read2(b);
                if (code < 0x10000) {
                    dest.append((char) code);
                } else if (code <= 0x10ffff) { // Surrogates.
                    dest.append((char) (((code - 0x10000) >> 10) + 0xd800));
                    dest.append((char) (((code - 0x10000) & 0x3ff) + 0xdc00));
                } else {
                    throw new CharConversionException("Cannot convert U+"
                            + Integer.toHexString(code)
                            + " to char (code greater than U+10FFFF)");
                }
            }
        }
    }

    public void reset() {
        _byteBuffer = null;
        _code = 0;
        _moreBytes = 0;
    }

    /**
     * @deprecated Replaced by {@link #setInput(ByteBuffer)}
     * @param byteBuffer ByteBuffer to use as input
     * @return Reference to this UTF8ByteBufferReader
     */
    public UTF8ByteBufferReader setByteBuffer(ByteBuffer byteBuffer) {
        return this.setInput(byteBuffer);
    }

}