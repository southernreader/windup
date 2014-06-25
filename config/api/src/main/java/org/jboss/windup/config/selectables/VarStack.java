/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.windup.config.selectables;

import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.jboss.windup.config.GraphRewrite;
import org.jboss.windup.config.exception.IllegalTypeArgumentException;
import org.jboss.windup.graph.model.WindupVertexFrame;
import org.jboss.windup.util.exception.WindupException;

/**
 * A variables stack, and also "current values" -
 * keeps few layers of "key"->[vertices] maps, one per rule execution level,
 * and current "cursor" (to an iterable) for iterations.
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class VarStack
{
    /*
     * SelectionStack
     */
    Deque<Map<String, Iterable<WindupVertexFrame>>> deque = new LinkedList<>();
    Map<String, WindupVertexFrame> currents = new HashMap<>();

    /**
     *  Gets an instance from a OCP rewrite context; created during rule init phase.
     */
    public static VarStack instance(GraphRewrite event)
    {
        return (VarStack) event.getRewriteContext().get(VarStack.class);
    }

    
    /**
     * Add new variables layer on top of the stack.
     */
    public void push()
    {
        Map<String, Iterable<WindupVertexFrame>> newFrame = new HashMap<>();
        deque.push(newFrame);
    }

    /**
     * Remove the top variables layer from the the stack.
     */
    public Map<String, Iterable<WindupVertexFrame>> pop()
    {
        return deque.pop();
    }

    /**
     *  Get the top variables layer from the stack.
     */
    private Map<String, Iterable<WindupVertexFrame>> peek()
    {
        return deque.peek();
    }

    /**
     *  Set a variable in the top variables layer to given "collection" of the vertex frames.
     *  Can't be reassigned - throws on attempt to reassign.
     */
    public void setVariable(String name, Iterable<WindupVertexFrame> iterable)
    {
        Map<String, Iterable<WindupVertexFrame>> frame = peek();
        if (findVariable(name) != null)
            throw new IllegalArgumentException("Variable \"" + name
                        + "\" has already been assigned and cannot be reassigned");

        frame.put(name, iterable);
    }

    
    /**
     * Type-safe wrapper around findVariable which gives only one framed vertex, 
     * and checks if there is 0 or 1; throws otherwise.
     */
    @SuppressWarnings("unchecked")
    public <T extends WindupVertexFrame> T findSingletonVariable(Class<T> type, String name)
    {
        Iterable<WindupVertexFrame> frames = findVariable(name);
        if( null == frames )
            throw new WindupException("Variable not found: " + name);
        
        Iterator<WindupVertexFrame> iterator = frames.iterator();
        if( ! iterator.hasNext() )
            return null;
        
        Object obj = iterator.next();
        
        // Check if there's just 1.
        if( iterator.hasNext() )
            throw new WindupException("More than one frame present "
                    + "under presumed singleton variable: " + name);

        // Check the type.
        if( ! type.isAssignableFrom(obj.getClass()) )
            throw new IllegalTypeArgumentException(name, type, obj.getClass());

        return (T) obj;
    }

    
    /**
     * Searches the variables layers, top to bottom, for given name,
     * and returns if found; null otherwise.
     */
    public Iterable<WindupVertexFrame> findVariable(String name)
    {
        Iterator<Map<String, Iterable<WindupVertexFrame>>> descIter = deque.descendingIterator();
        Iterable<WindupVertexFrame> result = null;
        while (descIter.hasNext())
        {
            Map<String, Iterable<WindupVertexFrame>> frame = descIter.next();
            result = frame.get(name);
            if (result != null) {
                break;
            }
        }
        return result;
    }

    
    /**
     * Sets the "cursor" for given variable to given framed vertex; no validity checks!
     */
    public void setCurrentPayload(String name, WindupVertexFrame element)
    {
        currents.put(name, element);
    }

    
    /**
     *  Returns the "cursor" for given var name.
     *  The variables typically keep an iterable; the "current payload" concept
     *  holds the reference to the currently iterated vertex.
     */
    @SuppressWarnings("unchecked")
    public <T extends WindupVertexFrame> T getCurrentPayload(Class<T> type, String name)
    {
        Object object = currents.get(name);
        if (object == null)
            return null;
        
        if (!type.isAssignableFrom(object.getClass()))
            throw new IllegalTypeArgumentException(name, type, object.getClass());

        return (T) object;
    }
}