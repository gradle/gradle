/**
 * Expressive Code plugin: "View source on GitHub" button.
 *
 * Any code block whose fence/meta carries `sourceUrl="https://…"` gets a
 * GitHub-mark button next to Expressive Code's copy button. The sample
 * components (<SampleScripts>/<SampleFile>) compute the per-variant URL from
 * the `source_dir` prop the converter emits and pass it via the <Code>
 * component's `meta` prop.
 *
 * Styling strategy: the element is a real <button> injected into the frames
 * plugin's `.copy` container, so every EC copy-button rule (layered
 * background, border, sizing, theming, and the hidden-until-frame-hover
 * reveal) applies to it verbatim — nothing is duplicated. We only override
 * the ::after icon mask (GitHub mark instead of the copy icon) and wire the
 * click to open the URL. EC's copy handler ignores it (no data-code attr).
 *
 * Plain .mjs: imported by ec.config.mjs, which is loaded outside Vite.
 */
import { definePlugin } from '@expressive-code/core';
import { h } from '@expressive-code/core/hast';

const GITHUB_ICON =
  `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='black'%3E%3Cpath d='M12,0.297c-6.63,0-12,5.373-12,12c0,5.303,3.438,9.8,8.205,11.385c0.6,0.113,0.82-0.258,0.82-0.577c0-0.285-0.01-1.04-0.015-2.04c-3.338,0.724-4.042-1.61-4.042-1.61c-0.546-1.385-1.335-1.755-1.335-1.755c-1.087-0.744,0.084-0.729,0.084-0.729c1.205,0.084,1.838,1.236,1.838,1.236c1.07,1.835,2.809,1.305,3.495,0.998c0.108-0.776,0.417-1.305,0.76-1.605c-2.665-0.3-5.466-1.332-5.466-5.93c0-1.31,0.465-2.38,1.235-3.22c-0.135-0.303-0.54-1.523,0.105-3.176c0,0,1.005-0.322,3.3,1.23c0.96-0.267,1.98-0.399,3-0.405c1.02,0.006,2.04,0.138,3,0.405c2.28-1.552,3.285-1.23,3.285-1.23c0.645,1.653,0.24,2.873,0.12,3.176c0.765,0.84,1.23,1.91,1.23,3.22c0,4.61-2.805,5.625-5.475,5.92c0.42,0.36,0.81,1.096,0.81,2.22c0,1.606-0.015,2.896-0.015,3.286c0,0.315,0.21,0.69,0.825,0.57C20.565,22.092,24,17.592,24,12.297C24,5.67,18.627,0.297,12,0.297z'/%3E%3C/svg%3E")`;

export function pluginViewSource() {
  return definePlugin({
    name: 'gradle-view-source',
    baseStyles: `
      .copy button.gd-view-source::after {
        -webkit-mask-image: ${GITHUB_ICON} !important;
        mask-image: ${GITHUB_ICON} !important;
        -webkit-mask-size: contain;
        mask-size: contain;
        -webkit-mask-position: center;
        mask-position: center;
      }
    `,
    hooks: {
      postprocessRenderedBlock: ({ codeBlock, renderData }) => {
        const url = codeBlock.metaOptions?.getString?.('sourceUrl');
        if (!url) {
          return;
        }
        const btn = h(
          'button.gd-view-source',
          {
            type: 'button',
            title: 'View source on GitHub',
            'aria-label': 'View source on GitHub',
            // stopImmediatePropagation keeps EC's copy-button click handler
            // (bound to every `.copy button`) from also firing on this one.
            onclick: `event.stopImmediatePropagation(); window.open(${JSON.stringify(url)}, '_blank', 'noopener')`,
          },
          [h('div')],
        );
        const placed = (function findCopy(node) {
          if (node.type === 'element') {
            const cls = node.properties?.className;
            if (Array.isArray(cls) ? cls.includes('copy') : cls === 'copy') {
              node.children.unshift(btn);
              return true;
            }
            for (const child of node.children ?? []) {
              if (findCopy(child)) return true;
            }
          }
          return false;
        })(renderData.blockAst);
        if (!placed) {
          renderData.blockAst.children.push(btn);
        }
      },
    },
  });
}
