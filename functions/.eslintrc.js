module.exports = {
    env: {
        es6: true,
        node: true,
    },
    parserOptions: {
        ecmaVersion: 2020,
        sourceType: "script",
    },
    extends: [
        "eslint:recommended",
        "google",
    ],
    rules: {
        "no-restricted-globals": ["error", "name", "length"],
        "prefer-arrow-callback": "error",
        "quotes": ["error", "double", {allowTemplateLiterals: true}],
        "indent": ["error", 4],
        "max-len": ["error", {
            code: 400,
            tabWidth: 4,
            ignoreUrls: true,
            ignoreStrings: true,
            ignoreTemplateLiterals: true,
        }],
        "eol-last": ["error", "always"],
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
