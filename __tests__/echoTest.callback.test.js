'use strict';

const { loadPlugin } = require('./helpers/loadPlugin');

describe('SQLiteFactory.echoTest', () => {
  test('calls SQLite.echoStringValue and succeeds on matching echo', () => {
    const native = {
      echoStringValue: jest.fn((opts, success) => success(opts.value)),
    };
    const { factory } = loadPlugin(native);
    const ok = jest.fn();
    const err = jest.fn();
    factory.echoTest(ok, err);
    expect(native.echoStringValue).toHaveBeenCalledTimes(1);
    const [opts] = native.echoStringValue.mock.calls[0];
    expect(opts).toEqual({ value: 'test-string' });
    expect(ok).toHaveBeenCalledTimes(1);
    expect(err).not.toHaveBeenCalled();
  });

  test('surfaces a mismatch via the error callback', () => {
    const native = {
      echoStringValue: jest.fn((_opts, success) => success('wrong-echo')),
    };
    const { factory } = loadPlugin(native);
    const ok = jest.fn();
    const err = jest.fn();
    factory.echoTest(ok, err);
    expect(ok).not.toHaveBeenCalled();
    expect(err).toHaveBeenCalledTimes(1);
    const [msg] = err.mock.calls[0];
    expect(String(msg)).toMatch(/Mismatch/i);
  });
});
