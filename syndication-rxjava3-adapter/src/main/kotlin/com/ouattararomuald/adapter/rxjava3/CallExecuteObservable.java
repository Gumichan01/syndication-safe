/*
 * Copyright (C) 2016 Jake Wharton
 * Copyright (C) 2019 Romuald Ouattara
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
package com.ouattararomuald.adapter.rxjava3;

import com.ouattararomuald.syndication.http.HttpException;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.exceptions.CompositeException;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import okhttp3.Call;
import okhttp3.Response;

final class CallExecuteObservable<SyndicationType> extends Observable<SyndicationType> {
  private final Call originalCall;
  private final Parser<SyndicationType> parser;

  CallExecuteObservable(Call originalCall, Parser<SyndicationType> parser) {
    this.originalCall = originalCall;
    this.parser = parser;
  }

  @Override protected void subscribeActual(Observer<? super SyndicationType> observer) {
    // Since Call is a one-shot type, clone it for each new observer.
    Call call = originalCall.clone();
    CallDisposable disposable = new CallDisposable<>(call, parser);
    observer.onSubscribe(disposable);
    if (disposable.isDisposed()) {
      return;
    }

    boolean terminated = false;
    try (Response response = call.execute()) {
      if (!disposable.isDisposed()) {
        if (response.isSuccessful()) {
          observer.onNext(parser.parse(response.body().string()));
        } else {
          throw new HttpException("Unsuccessful request");
        }
      }
      if (!disposable.isDisposed()) {
        terminated = true;
        observer.onComplete();
      }
    } catch (Throwable t) {
      Exceptions.throwIfFatal(t);
      if (terminated) {
        RxJavaPlugins.onError(t);
      } else if (!disposable.isDisposed()) {
        try {
          observer.onError(t);
        } catch (Throwable inner) {
          Exceptions.throwIfFatal(inner);
          RxJavaPlugins.onError(new CompositeException(t, inner));
        }
      }
    }
  }

  private static final class CallDisposable<SyndicationType> implements Disposable {
    private final Call call;
    private final Parser<SyndicationType> parser;
    private volatile boolean disposed;

    CallDisposable(Call call, Parser<SyndicationType> parser) {
      this.call = call;
      this.parser = parser;
    }

    @Override public void dispose() {
      disposed = true;
      call.cancel();
    }

    @Override public boolean isDisposed() {
      return disposed;
    }
  }
}
