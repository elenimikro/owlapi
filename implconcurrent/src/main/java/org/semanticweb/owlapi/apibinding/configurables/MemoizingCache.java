package org.semanticweb.owlapi.apibinding.configurables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class MemoizingCache<A, V> implements Map<A, V> {
	private final ConcurrentHashMap<A, FutureTask<V>> cache = new ConcurrentHashMap<A, FutureTask<V>>();

	public V get(final Computable<V> computant, final A key) {
		while (true) {
			FutureTask<V> f = cache.get(key);
			if (f == null) {
				Callable<V> eval = new Callable<V>() {
					public V call() {
						V compute = computant.compute();
						return compute;
					}
				};
				FutureTask<V> ft = new FutureTask<V>(eval);
				f = cache.putIfAbsent(key, ft);
				if (f == null) {
					f = ft;
					ft.run();
				}
			}
			try {
				return f.get();
			} catch (CancellationException e) {
				cache.remove(key, f);
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			} catch (InterruptedException e) {
				cache.remove(key);
				throw new RuntimeException("Unexpected interrupted exception",
						e);
			}
		}
	}

	public V get(final V computed, final A key) {
		while (true) {
			FutureTask<V> f = cache.get(key);
			if (f == null) {
				Callable<V> eval = new Callable<V>() {
					public V call() {
						return computed;
					}
				};
				FutureTask<V> ft = new FutureTask<V>(eval);
				f = cache.putIfAbsent(key, ft);
				if (f == null) {
					f = ft;
					ft.run();
				}
			}
			try {
				return f.get();
			} catch (CancellationException e) {
				cache.remove(key, f);
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			} catch (InterruptedException e) {
				cache.remove(key);
				throw new RuntimeException("Unexpected interrupted exception",
						e);
			}
		}
	}

	public void clear() {
		this.cache.clear();
	}

	public boolean containsKey(Object key) {
		return this.cache.containsKey(key);
	}

	public boolean containsValue(Object value) {
		for (Future<V> f : cache.values()) {
			try {
				if (f.get().equals(value)) {
					return true;
				}
			} catch (InterruptedException e) {
				// nothing to do here
			} catch (ExecutionException e) {
				// nothing to do here
			}
		}
		return false;
	}

	public Set<java.util.Map.Entry<A, V>> entrySet() {
		throw new UnsupportedOperationException("EntrySet not available");
	}

	public V get(Object key) {
		if (!cache.containsKey(key)) {
			return null;
		}
		// the run for the future task is supposed to have already been performed, so no exceptions are expected or managed here
		try {
			return cache.get(key).get();
		} catch (CancellationException e) {
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean isEmpty() {
		return cache.isEmpty();
	}

	public Set<A> keySet() {
		return cache.keySet();
	}

	/**
	 * This method should not be used for a MemoizingCache; the preferred way is
	 * to provide a Computable and use the get(Computable, A) method Notice that
	 * the returned value might not be the last previous stored value but one of
	 * the values stored before the new value
	 */
	public V put(A key, final V value) {
		V toReturn = this.get(key);
		this.get(value, key);
		return toReturn;
	}

	public void putAll(Map<? extends A, ? extends V> t) {
		throw new UnsupportedOperationException(
				"Adding values must be done through the get(Computable<A,V>, A) method");
	}

	public V remove(Object key) {
		V f = get(key);
		cache.remove(key);
		return f;
	}

	public int size() {
		return cache.size();
	}

	public Collection<V> values() {
		List<V> toReturn = new ArrayList<V>();
		for (A key : cache.keySet()) {
			toReturn.add(get(key));
		}
		return toReturn;
	}
}