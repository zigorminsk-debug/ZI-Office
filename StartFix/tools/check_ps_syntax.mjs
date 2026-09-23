#!/usr/bin/env node
/**
 * Проверка синтаксиса PowerShell-скриптов утилиты (грамматика tree-sitter).
 * Требуется: npm install tree-sitter tree-sitter-powershell
 * Запуск: node tools/check_ps_syntax.mjs scripts/*.ps1
 */
import fs from 'fs';
import Parser from 'tree-sitter';
import PS from 'tree-sitter-powershell';

const parser = new Parser();
parser.setLanguage(PS);

let total = 0;
for (const file of process.argv.slice(2)) {
  const src = fs.readFileSync(file, 'utf8');
  const tree = parser.parse(src);
  const errors = [];
  const walk = (node) => {
    if (node.type === 'ERROR' || node.isMissing) {
      errors.push({ start: node.startPosition, missing: node.isMissing,
                    text: src.slice(node.startIndex, Math.min(node.endIndex, node.startIndex + 100)) });
    }
    for (let i = 0; i < node.childCount; i++) walk(node.child(i));
  };
  walk(tree.rootNode);
  if (errors.length === 0) {
    console.log(`  ${file}: синтаксис в порядке`);
  } else {
    console.log(`  ${file}: ${errors.length} ошибок`);
    for (const e of errors.slice(0, 20)) {
      console.log(`     строка ${e.start.row + 1}:${e.start.column + 1}${e.missing ? ' (пропущено)' : ''} -> ${JSON.stringify(e.text.slice(0, 80))}`);
    }
  }
  total += errors.length;
}
process.exit(total === 0 ? 0 : 1);
