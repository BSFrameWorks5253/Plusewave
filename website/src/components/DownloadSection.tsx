import React from 'react';
import { Download, Sparkles, ExternalLink, HeartHandshake } from 'lucide-react';

interface DownloadSectionProps {
  onOpenUpi: () => void;
}

export const DownloadSection: React.FC<DownloadSectionProps> = ({ onOpenUpi }) => {
  return (
    <section id="download" className="py-24 px-4 sm:px-6 lg:px-8 max-w-6xl mx-auto text-center relative z-10">
      <div className="liquid-glass rounded-3xl p-8 sm:p-14 border border-white/15 relative overflow-hidden shadow-2xl">
        
        {/* Glow Spheres */}
        <div className="absolute -top-24 -right-24 w-80 h-80 rounded-full bg-purple-600/20 filter blur-3xl pointer-events-none"></div>
        <div className="absolute -bottom-24 -left-24 w-80 h-80 rounded-full bg-cyan-500/20 filter blur-3xl pointer-events-none"></div>

        <div className="relative z-10 max-w-2xl mx-auto space-y-6">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full liquid-glass-pill text-xs font-mono font-semibold tracking-wider text-cyan-300 uppercase border border-cyan-500/20">
            <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
            <span>Ready for Pure Sound?</span>
          </div>

          <h2 className="text-4xl sm:text-6xl font-extrabold tracking-tight text-white leading-tight">
            Install Pulse Wave <br />
            <span className="accent-gradient">in seconds.</span>
          </h2>

          <p className="text-slate-300 text-sm sm:text-base leading-relaxed">
            Download the latest compiled release directly from GitHub Releases. Compatible with phones, foldables, and tablets on Android 8.0+.
          </p>

          {/* Action Buttons */}
          <div className="flex flex-wrap items-center justify-center gap-4 pt-4">
            <a
              href="https://github.com/BSFrameworks5253/Plusewave/releases/latest"
              target="_blank"
              rel="noreferrer"
              className="btn-primary-gradient px-8 py-4 rounded-2xl text-base font-extrabold text-white flex items-center gap-3 shadow-2xl cursor-pointer"
            >
              <Download className="w-5 h-5" />
              <span>Download Latest Build (.APK)</span>
            </a>

            <button
              onClick={onOpenUpi}
              className="liquid-glass liquid-glass-hover px-6 py-4 rounded-2xl text-base font-bold text-slate-200 hover:text-white flex items-center gap-2.5 cursor-pointer"
            >
              <HeartHandshake className="w-5 h-5 text-pink-400" />
              <span>Sponsor via UPI</span>
            </button>
          </div>

          {/* Badges Grid */}
          <div className="flex flex-wrap items-center justify-center gap-2 pt-6">
            <img src="https://img.shields.io/badge/%E2%AC%87%EF%B8%8F%20DOWNLOAD%20PULSE%20WAVE-4CAF50?style=for-the-badge&logo=android&logoColor=white" alt="Download Badge" />
            <img src="https://img.shields.io/github/downloads/BSFrameworks5253/Plusewave/total?style=for-the-badge&color=blue" alt="Total Downloads" />
            <img src="https://img.shields.io/badge/Discord-Join%20Us-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" />
            <img src="https://img.shields.io/github/license/BSFrameworks5253/Plusewave?style=for-the-badge" alt="GPL-3.0 License" />
          </div>
        </div>

      </div>
    </section>
  );
};
