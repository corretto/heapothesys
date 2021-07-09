// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

/**
 * The representation of an allocated object with fixed size in heap. It can also have a reference
 * to another AllocObject to allow us creating a complex reachability graph among them.
 */
class PlainObject extends DataObject implements AllocObject {

    private AllocObject next;

    PlainObject(final int size, final AllocObject ref) {
        super(size);
        this.next = ref;
    }

    /**
     * Set the next object referenced by the current one.
     *
     * @param next The object referenced by the current one.
     */
    @Override
    public void setNext(final AllocObject next) {
        this.next = next;
    }

    /**
     * Get the object referenced by the current one.
     *
     * @return The referenced object.
     */
    public AllocObject getNext() {
        return next;
    }
}
