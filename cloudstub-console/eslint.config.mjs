// @ts-check
import tseslint from 'typescript-eslint';
import angular from '@angular-eslint/eslint-plugin';
import angularTemplate from '@angular-eslint/eslint-plugin-template';
import templateParser from '@angular-eslint/template-parser';

export default tseslint.config(
  {
    files: ['**/*.ts'],
    extends: [...tseslint.configs.recommended],
    plugins: { '@angular-eslint': angular },
    rules: {
      ...angular.configs.recommended.rules,
    },
  },
  {
    files: ['**/*.html'],
    languageOptions: { parser: templateParser },
    plugins: { '@angular-eslint/template': angularTemplate },
    rules: {
      ...angularTemplate.configs.recommended.rules,
    },
  },
  {
    ignores: ['dist/**', '.angular/**', 'node_modules/**'],
  },
);
