import React from 'react';

export const Footer: React.FC = () => {
  return (
    <footer className="border-t border-white/5 py-12 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto relative z-10">
      <div className="flex flex-col md:flex-row items-center justify-between gap-6 text-xs text-slate-400">
        
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl p-0.5 bg-gradient-to-tr from-cyan-500 to-purple-600 flex items-center justify-center">
            <img src="./assets/logo.png" alt="Pulse Wave" className="w-full h-full object-contain" />
          </div>
          <div>
            <p className="font-semibold text-slate-300">Pulse Wave © 2026</p>
            <p className="text-[11px] text-slate-500">Maintained by <b>BS.Framework5253</b>. Licensed under GPL-3.0.</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-center gap-6">
          <a
            href="https://support-akshansh.vercel.app"
            target="_blank"
            rel="noreferrer"
            className="hover:text-cyan-400 transition-colors"
          >
            support-akshansh.vercel.app
          </a>
          <a
            href="https://github.com/BSFrameworks5253/Plusewave"
            target="_blank"
            rel="noreferrer"
            className="hover:text-cyan-400 transition-colors"
          >
            Source Code
          </a>
          <a
            href="https://github.com/BSFrameworks5253/Plusewave/issues"
            target="_blank"
            rel="noreferrer"
            className="hover:text-cyan-400 transition-colors"
          >
            Report an Issue
          </a>
          <a
            href="https://discord.gg/spbuDTePSR"
            target="_blank"
            rel="noreferrer"
            className="hover:text-cyan-400 transition-colors"
          >
            Discord Community
          </a>
        </div>

      </div>

      <div className="mt-8 pt-6 border-t border-white/5 text-[11px] text-slate-500 text-center">
        Disclaimer: Pulse Wave is an independent open-source project. Not affiliated with, endorsed by, or associated with Google LLC, YouTube, or Spotify.
      </div>
    </footer>
  );
};
