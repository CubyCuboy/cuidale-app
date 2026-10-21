module.exports = {
  env: {
    es6: true,
    es2022: true,
    node: true,
  },
  parserOptions: {
    ecmaVersion: 2022,
  },
  extends: [
    "eslint:recommended",
    "google",
  ],
  rules: {
    "no-restricted-globals": ["error", "name", "length"],
    "prefer-arrow-callback": "off",
    "quotes": "off",
    "max-len": "off",
    "object-curly-spacing": "off",
    "require-jsdoc": "off",
    "comma-dangle": "off",
    "indent": "off",
    "arrow-parens": "off",
    "space-before-function-paren": "off",
    "block-spacing": "off",
    "brace-style": "off",
    "operator-linebreak": "off",
    "no-unused-vars": ["warn", { "argsIgnorePattern": "^_" }],
  },
  overrides: [
    {
      files: ["**/*.spec.*"],
      env: {
        mocha: true,
      },
      rules: {},
    },
  ],
  globals: {},
};
