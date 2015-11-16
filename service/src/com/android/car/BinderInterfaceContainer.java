/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car;

import android.annotation.Nullable;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.Collection;
import java.util.HashMap;

/**
 * Helper class to hold client's binder interface.
 */
public class BinderInterfaceContainer<T extends IInterface> {

    public static class BinderInterface<T extends IInterface>
            implements IBinder.DeathRecipient {
        public final T binderInterface;
        public final int version;
        private final BinderInterfaceContainer<T> mContainer;

        private BinderInterface(BinderInterfaceContainer<T> container, T binderInterface,
                int version) {
            mContainer = container;
            this.binderInterface = binderInterface;
            this.version = version;
        }

        @Override
        public void binderDied() {
            binderInterface.asBinder().unlinkToDeath(this, 0);
        }
    }

    public interface BinderEventHandler<T extends IInterface> {
        void onBinderDeath(BinderInterface<T> bInterface);
    }

    private final BinderEventHandler<T> mEventHandler;
    private final HashMap<IBinder, BinderInterface<T>> mBinders = new HashMap<>();

    public BinderInterfaceContainer(@Nullable BinderEventHandler<T> eventHandler) {
        mEventHandler = eventHandler;
    }

    public void addBinder(int version, T binderInterface) {
        IBinder binder = binderInterface.asBinder();
        synchronized (this) {
            BinderInterface bInterface = mBinders.get(binder);
            if (bInterface != null) {
                return;
            }
            bInterface = new BinderInterface(this, binderInterface, version);
            try {
                binder.linkToDeath(bInterface, 0);
            } catch (RemoteException e) {
                throw new IllegalArgumentException(e);
            }
            mBinders.put(binder, bInterface);
        }
    }

    public void removeBinder(T binderInterface) {
        IBinder binder = binderInterface.asBinder();
        synchronized(this) {
            BinderInterface bInterface = mBinders.get(binder);
            if (bInterface != null) {
                return;
            }
            binder.unlinkToDeath(bInterface, 0);
            mBinders.remove(binder);
        }
    }

    public Collection<BinderInterface<T>> getInterfaces() {
        synchronized (this) {
            return mBinders.values();
        }
    }

    public synchronized void release() {
        Collection<BinderInterface<T>> interfaces = getInterfaces();
        for (BinderInterface<T> bInterface : interfaces) {
            removeBinder(bInterface.binderInterface);
        }
    }

    private void handleBinderDeath(BinderInterface<T> bInterface) {
        removeBinder(bInterface.binderInterface);
        if (mEventHandler != null) {
            mEventHandler.onBinderDeath(bInterface);
        }
    }
}
