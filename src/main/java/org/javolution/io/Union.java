/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2012 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */
package org.javolution.io;

/**
 * <p> 等同于 <code>C/C++ union</code>；此类的工作方式与 {@link Struct}（子类）相同，
 *     不同之处在于所有成员都映射到内存中的同一位置。</p>
 * <p> 以下是 C 联合体的示例：
 * {@code
 * union Number {
 *     int   asInt;
 *     float asFloat;
 *     char  asString[12];
 * };}</p>
 * <p> 对应的 Java 等效类如下：
 * {@code
 * public class Number extends Union {
 *     Signed32   asInt    = new Signed32();
 *     Float32    asFloat  = new Float32();
 *     Utf8String asString = new Utf8String(12);
 * }}</p>
 *  <p> 与任何 {@link Struct} 一样，字段可以直接访问：
 *  {@code
 *  Number num = new Number();
 *  num.asInt.set(23);
 *  num.asString.set("23"); // Null 终止（C 兼容）
 *  float f = num.asFloat.get();}</p>
 * 
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @version 1.0, October 4, 2004
 */
@SuppressWarnings("unused")
public abstract class Union extends Struct {

    /**
     * 默认构造函数。
     */
    public Union() {}

    /**
     * 返回 <code>true</code>。
     * 
     * @return <code>true</code>
     */
    public final boolean isUnion() {
        return true;
    }
}