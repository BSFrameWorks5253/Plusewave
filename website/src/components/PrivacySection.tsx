import React from 'react';
import { Shield, ShieldAlert, Check, X } from 'lucide-react';

export const PrivacySection: React.FC = () => {
  return (
    <section id="privacy" className="py-24 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto relative z-10">
      
      {/* Header */}
      <div className="text-center max-w-3xl mx-auto mb-16 space-y-4">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full liquid-glass-pill text-xs font-mono font-semibold tracking-wider text-emerald-400 uppercase border border-emerald-500/20">
          <Shield className="w-3.5 h-3.5 text-emerald-400" />
          <span>Privacy Vault</span>
        </div>
        <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white">
          Zero Trackers. Zero Telemetry. <br />
          <span className="text-emerald-400">100% On-Device.</span>
        </h2>
        <p className="text-slate-400 text-sm sm:text-base leading-relaxed">
          Your music taste is personal. Here is how Pulse Wave transparently compares to corporate streaming applications.
        </p>
      </div>

      {/* Comparison Matrix Table */}
      <div className="liquid-glass rounded-3xl overflow-hidden border border-white/10 shadow-2xl max-w-4xl mx-auto">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="border-b border-white/10 bg-white/5 text-slate-300 font-mono text-xs uppercase">
                <th className="p-5 font-bold">Privacy & Feature Spec</th>
                <th className="p-5 text-cyan-400 font-bold bg-cyan-500/10">Pulse Wave</th>
                <th className="p-5 text-slate-400">Spotify</th>
                <th className="p-5 text-slate-400">YT Music Official</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-slate-300">
              <tr>
                <td className="p-5 font-semibold text-white">Data Analytics & SDK Trackers</td>
                <td className="p-5 font-mono text-emerald-400 font-bold bg-cyan-500/5">
                  <div className="flex items-center gap-1.5">
                    <Check className="w-4 h-4 text-emerald-400" />
                    <span>0 Trackers</span>
                  </div>
                </td>
                <td className="p-5 text-slate-400">18+ SDK Trackers</td>
                <td className="p-5 text-slate-400">Google Ads & Telemetry</td>
              </tr>
              <tr>
                <td className="p-5 font-semibold text-white">In-App Commercial Advertisements</td>
                <td className="p-5 font-mono text-emerald-400 font-bold bg-cyan-500/5">
                  <div className="flex items-center gap-1.5">
                    <Check className="w-4 h-4 text-emerald-400" />
                    <span>100% Ad-Free</span>
                  </div>
                </td>
                <td className="p-5 text-slate-400">Audio & Visual Ads</td>
                <td className="p-5 text-slate-400">Commercial Audio Ads</td>
              </tr>
              <tr>
                <td className="p-5 font-semibold text-white">Background & Lock-Screen Playback</td>
                <td className="p-5 font-mono text-emerald-400 font-bold bg-cyan-500/5">
                  <div className="flex items-center gap-1.5">
                    <Check className="w-4 h-4 text-emerald-400" />
                    <span>Included Free</span>
                  </div>
                </td>
                <td className="p-5 text-slate-400">Limited on Free</td>
                <td className="p-5 text-red-400 font-medium">Locked (Premium $11/mo)</td>
              </tr>
              <tr>
                <td className="p-5 font-semibold text-white">Playlists & Favorites Storage</td>
                <td className="p-5 font-mono text-emerald-400 font-bold bg-cyan-500/5">
                  <div className="flex items-center gap-1.5">
                    <Check className="w-4 h-4 text-emerald-400" />
                    <span>100% Local Encrypted</span>
                  </div>
                </td>
                <td className="p-5 text-slate-400">Server Profile Bound</td>
                <td className="p-5 text-slate-400">Google Account Bound</td>
              </tr>
              <tr>
                <td className="p-5 font-semibold text-white">License & Source Code Auditability</td>
                <td className="p-5 font-mono text-emerald-400 font-bold bg-cyan-500/5">
                  <div className="flex items-center gap-1.5">
                    <Check className="w-4 h-4 text-emerald-400" />
                    <span>GPL-3.0 Open-Source</span>
                  </div>
                </td>
                <td className="p-5 text-slate-400">Proprietary Closed</td>
                <td className="p-5 text-slate-400">Proprietary Closed</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </section>
  );
};
