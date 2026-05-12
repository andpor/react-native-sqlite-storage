'use strict';

const NativeModules = {};
const TurboModuleRegistry = {
  get: jest.fn(() => null),
  getEnforcing: jest.fn((name) => {
    throw new Error(`TurboModuleRegistry.getEnforcing mock: '${name}' not available`);
  }),
};

module.exports = {
  NativeModules,
  TurboModuleRegistry,
};
