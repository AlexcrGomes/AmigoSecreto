import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { resetMedia, setMediaMatcher } from '../test/media';
import { useMediaQuery } from './useMediaQuery';

describe('useMediaQuery', () => {
  afterEach(() => resetMedia());

  it('devolve o estado atual da consulta', () => {
    setMediaMatcher((query) => query === '(min-width: 768px)');

    expect(renderHook(() => useMediaQuery('(min-width: 768px)')).result.current).toBe(true);
    expect(renderHook(() => useMediaQuery('(prefers-reduced-motion: reduce)')).result.current).toBe(
      false,
    );
  });

  it('atualiza quando a consulta muda (girar o tablet, trocar a preferência)', () => {
    const { result } = renderHook(() => useMediaQuery('(min-width: 768px)'));
    expect(result.current).toBe(false);

    act(() => setMediaMatcher((query) => query === '(min-width: 768px)'));
    expect(result.current).toBe(true);

    act(() => setMediaMatcher(() => false));
    expect(result.current).toBe(false);
  });
});
