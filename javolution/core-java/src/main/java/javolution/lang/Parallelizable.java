/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2012 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */
package javolution.lang;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p> Indicates that a class, a method or a field can be used by multiple 
 *     threads concurrently and whether or not it is 
 *     {@link Parallelizable#mutexFree() mutex-free} (not blocking).
 * <pre>{@code
 * public class Operators {
 *    {@literal@}Parallelizable
 *    public static final Reducer<Object> ANY = new Reducer<Object>() { ... }
 *    
 *    {@literal@}Parallelizable(mutexFree = false, comment="Internal use of synchronization")
 *    public static final Reducer<Object> MAX = new Reducer<Object>() { ... }
 *    
 *    {@literal@}Parallelizable(mutexFree = false, comment="Internal use of synchronization")
 *    public static final Reducer<Object> MIN = new Reducer<Object>() { ... }
 *    
 *    {@literal@}Parallelizable
 *    public static final Reducer<Boolean>; AND = new Reducer<Boolean>() { ... }
 *    
 *    {@literal@}Parallelizable
 *    public static final Reducer<Boolean> OR = new Reducer<Boolean>() { ... }
 *    
 *    {@literal@}Parallelizable(comment="Internal use of AtomicInteger")
 *    public static final Reducer<Integer> SUM = new Reducer<Integer>() { ... }
 * }</pre></p>
 *  
 * <p> Classes with no internal fields or Immutable are usually parallelizable and mutex-free.</p>
 *
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @version 6.0, July 21, 2013
 * @see <a href="http://en.wikipedia.org/wiki/Mutual_exclusion">Wikipedia: Mutual Exclusion</a>
 */
@Documented
@Inherited
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Parallelizable {

    /**
     * Indicates if this element can safely be used concurrently 
     * (default {@code true}).
     * @return true if the element can safely be used concurrently, false otherwise
     */
    boolean value() default true;

    /**
     * Indicates if this element does not use any form of mutex to 
     * access shared resources (default {@code true}). To avoid 
     * <a href="http://en.wikipedia.org/wiki/Priority_inversion">
     * priority inversion</a> and possibly unbounded response times,
     * a real-time VM (with priority inheritance) is recommended
     * when using {@link Realtime real-time} elements which are not mutex-free.
     * @return true if mutex free, false otherwise
     */
    boolean mutexFree() default true;

    /**
     * Provides additional information (default {@code ""}).
     * @return comment providing additional information
     */
    String comment() default "";

}
