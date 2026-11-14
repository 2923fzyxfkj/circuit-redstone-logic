# 离线查看存档：找出所有扫描器，并按它自己的规则数一遍左/右/上 15 格内的非空气方块。
# 用于回答"这个扫描器到底数到了哪几个方块"这类问题（不需要启动游戏）。
#
# 用法：pwsh -File tools/InspectWorld.ps1 -WorldDir run\saves\modtest

param(
	[string]$WorldDir = 'run\saves\modtest',
	[string]$BlockName = 'circuit-redstone_logic:scanner'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$worldPath = Join-Path $root $WorldDir
if (-not (Test-Path $worldPath)) {
	throw "找不到存档目录: $worldPath"
}

$code = @'
using System;
using System.Collections.Generic;
using System.Text;

public class CrWorldInspect
{
    class Palette { public string Name; public Dictionary<string,string> Props; }
    class Section
    {
        public List<Palette> Palette;
        public ulong[] Data;
        public int Bits;
        public int PerLong;
        public ulong Mask;
        public int StateAt(int lx, int ly, int lz)
        {
            if (Data == null) return 0;
            int idx = (ly * 16 + lz) * 16 + lx;
            int li = idx / PerLong;
            int off = idx % PerLong;
            if (li >= Data.Length) return 0;
            return (int)((Data[li] >> (off * Bits)) & Mask);
        }
    }
    class Chunk { public Dictionary<int, Section> Sections = new Dictionary<int, Section>(); }

    static readonly Dictionary<long, Chunk> chunks = new Dictionary<long, Chunk>();

    static long Key(int cx, int cz) { return ((long)cx << 32) ^ (uint)cz; }
    static int BitsNeeded(int count) { int bits = 0; int v = count - 1; while (v > 0) { bits++; v >>= 1; } return Math.Max(1, bits); }
    static int FloorDiv(int a, int b) { int q = a / b; if ((a % b != 0) && ((a < 0) != (b < 0))) q--; return q; }

    class NbtReader
    {
        readonly byte[] d; int p;
        public NbtReader(byte[] data) { d = data; p = 0; }
        public object ReadRoot()
        {
            byte t = d[p++];
            if (t != 10) throw new Exception("root is not a compound");
            ReadString();
            return ReadPayload(10);
        }
        int ReadInt() { int v = (d[p] << 24) | (d[p + 1] << 16) | (d[p + 2] << 8) | d[p + 3]; p += 4; return v; }
        long ReadLong() { long v = 0; for (int i = 0; i < 8; i++) { v = (v << 8) | (long)d[p + i]; } p += 8; return v; }
        string ReadString() { int len = (d[p] << 8) | d[p + 1]; p += 2; var s = Encoding.UTF8.GetString(d, p, len); p += len; return s; }
        object ReadPayload(byte type)
        {
            switch (type)
            {
                case 1: { var v = (sbyte)d[p]; p++; return v; }
                case 2: { short v = (short)((d[p] << 8) | d[p + 1]); p += 2; return v; }
                case 3: { return ReadInt(); }
                case 4: { return ReadLong(); }
                case 5: { int v = ReadInt(); return BitConverter.Int32BitsToSingle(v); }
                case 6: { long v = ReadLong(); return BitConverter.Int64BitsToDouble(v); }
                case 7: { int len = ReadInt(); var a = new byte[len]; Array.Copy(d, p, a, 0, len); p += len; return a; }
                case 8: { return ReadString(); }
                case 9:
                {
                    byte et = d[p++];
                    int len = ReadInt();
                    var list = new List<object>();
                    for (int i = 0; i < len; i++) list.Add(ReadPayload(et));
                    return list;
                }
                case 10:
                {
                    var dict = new Dictionary<string, object>();
                    while (true)
                    {
                        byte t = d[p++];
                        if (t == 0) break;
                        string name = ReadString();
                        dict[name] = ReadPayload(t);
                    }
                    return dict;
                }
                case 11: { int len = ReadInt(); var a = new int[len]; for (int i = 0; i < len; i++) a[i] = ReadInt(); return a; }
                case 12: { int len = ReadInt(); var a = new long[len]; for (int i = 0; i < len; i++) a[i] = ReadLong(); return a; }
                default: throw new Exception("unsupported nbt tag " + type);
            }
        }
    }

    public static void RegisterChunk(int cx, int cz, byte[] raw)
    {
        var root = (Dictionary<string, object>)new NbtReader(raw).ReadRoot();
        var chunk = new Chunk();
        object sectionsObj;
        if (root.TryGetValue("sections", out sectionsObj))
        {
            foreach (var secObj in (List<object>)sectionsObj)
            {
                var sec = (Dictionary<string, object>)secObj;
                int sy = Convert.ToInt32((sbyte)sec["Y"]);
                object bsObj;
                if (!sec.TryGetValue("block_states", out bsObj)) continue;
                var bs = (Dictionary<string, object>)bsObj;
                var palette = new List<Palette>();
                foreach (var peObj in (List<object>)bs["palette"])
                {
                    var pe = (Dictionary<string, object>)peObj;
                    var entry = new Palette { Name = (string)pe["Name"], Props = new Dictionary<string, string>() };
                    object propsObj;
                    if (pe.TryGetValue("Properties", out propsObj))
                        foreach (var kv in (Dictionary<string, object>)propsObj) entry.Props[kv.Key] = (string)kv.Value;
                    palette.Add(entry);
                }
                var section = new Section { Palette = palette, Bits = Math.Max(4, BitsNeeded(palette.Count)) };
                section.PerLong = 64 / section.Bits;
                section.Mask = (1UL << section.Bits) - 1UL;
                object dataObj;
                if (palette.Count > 1 && bs.TryGetValue("data", out dataObj))
                {
                    var longs = (long[])dataObj;
                    var arr = new ulong[longs.Length];
                    for (int i = 0; i < longs.Length; i++) arr[i] = unchecked((ulong)longs[i]);
                    section.Data = arr;
                }
                chunk.Sections[sy] = section;
            }
        }
        chunks[Key(cx, cz)] = chunk;
    }

    public static int ChunkCount() { return chunks.Count; }

    static Palette BlockAt(long x, long y, long z)
    {
        Chunk chunk;
        if (!chunks.TryGetValue(Key(FloorDiv((int)x, 16), FloorDiv((int)z, 16)), out chunk)) return null;
        Section sec;
        if (!chunk.Sections.TryGetValue((int)(y >> 4), out sec)) return null;
        int lx = (int)(x - (long)FloorDiv((int)x, 16) * 16);
        int lz = (int)(z - (long)FloorDiv((int)z, 16) * 16);
        int ly = (int)(y & 15);
        int idx = sec.StateAt(lx, ly, lz);
        if (idx < 0 || idx >= sec.Palette.Count) return null;
        return sec.Palette[idx];
    }

    static string RotCw(string dir)
    {
        switch (dir) { case "north": return "east"; case "east": return "south"; case "south": return "west"; default: return "north"; }
    }
    static string RotCcw(string dir)
    {
        switch (dir) { case "north": return "west"; case "west": return "south"; case "south": return "east"; default: return "north"; }
    }

    public static string ReportNamespace(string prefix, int maxPerType)
    {
        var sb = new StringBuilder();
        var byName = new Dictionary<string, List<string>>();
        foreach (var kv in chunks)
        {
            int cx = (int)(kv.Key >> 32);
            int cz = (int)(uint)(kv.Key & 0xFFFFFFFF);
            foreach (var secKv in kv.Value.Sections)
            {
                var sec = secKv.Value;
                bool has = false;
                foreach (var pe in sec.Palette) if (pe.Name != null && pe.Name.StartsWith(prefix)) { has = true; break; }
                if (!has) continue;
                for (int ly = 0; ly < 16; ly++)
                    for (int lz = 0; lz < 16; lz++)
                        for (int lx = 0; lx < 16; lx++)
                        {
                            int idx = sec.StateAt(lx, ly, lz);
                            if (idx < 0 || idx >= sec.Palette.Count) continue;
                            var pe = sec.Palette[idx];
                            if (pe.Name == null || !pe.Name.StartsWith(prefix)) continue;
                            List<string> list;
                            if (!byName.TryGetValue(pe.Name, out list)) { list = new List<string>(); byName[pe.Name] = list; }
                            var props = new StringBuilder();
                            foreach (var p in pe.Props) props.Append(" " + p.Key + "=" + p.Value);
                            list.Add("(" + (cx * 16 + lx) + "," + (secKv.Key * 16 + ly) + "," + (cz * 16 + lz) + ")" + props);
                        }
            }
        }
        foreach (var name in byName.Keys)
        {
            var list = byName[name];
            sb.AppendLine(name + "  count=" + list.Count);
            for (int i = 0; i < list.Count && i < maxPerType; i++) sb.AppendLine("  " + list[i]);
            if (list.Count > maxPerType) sb.AppendLine("  ... (" + (list.Count - maxPerType) + " more)");
        }
        if (byName.Count == 0) sb.AppendLine("(该前缀没有任何方块)");
        return sb.ToString();
    }

    public static string Report(string target)
    {
        var sb = new StringBuilder();
        var found = new List<long[]>();
        var props = new List<Dictionary<string, string>>();
        foreach (var kv in chunks)
        {
            int cx = (int)(kv.Key >> 32);
            int cz = (int)(uint)(kv.Key & 0xFFFFFFFF);
            foreach (var secKv in kv.Value.Sections)
            {
                var sec = secKv.Value;
                bool has = false;
                foreach (var pe in sec.Palette) if (pe.Name == target) { has = true; break; }
                if (!has) continue;
                for (int ly = 0; ly < 16; ly++)
                    for (int lz = 0; lz < 16; lz++)
                        for (int lx = 0; lx < 16; lx++)
                        {
                            int idx = sec.StateAt(lx, ly, lz);
                            if (idx < 0 || idx >= sec.Palette.Count) continue;
                            var pe = sec.Palette[idx];
                            if (pe.Name != target) continue;
                            found.Add(new long[] { cx * 16 + lx, secKv.Key * 16 + ly, cz * 16 + lz });
                            props.Add(pe.Props);
                        }
            }
        }
        sb.AppendLine("scanners found: " + found.Count);
        for (int i = 0; i < found.Count; i++)
        {
            long x = found[i][0], y = found[i][1], z = found[i][2];
            var p = props[i];
            string facing = p.ContainsKey("facing") ? p["facing"] : "north";
            string power = p.ContainsKey("power") ? p["power"] : "?";
            sb.AppendLine();
            sb.AppendLine("=== scanner at (" + x + ", " + y + ", " + z + ") facing=" + facing + " stored power=" + power + " ===");
            sb.AppendLine("  left  (" + RotCcw(facing) + "): " + CountRay(x, y, z, RotCcw(facing)));
            sb.AppendLine("  right (" + RotCw(facing) + "): " + CountRay(x, y, z, RotCw(facing)));
            sb.AppendLine("  up    (up): " + CountRay(x, y, z, "up"));
        }
        return sb.ToString();
    }

    static string CountRay(long x, long y, long z, string dir)
    {
        int dx = 0, dy = 0, dz = 0;
        switch (dir) { case "north": dz = -1; break; case "south": dz = 1; break; case "east": dx = 1; break; case "west": dx = -1; break; case "up": dy = 1; break; case "down": dy = -1; break; }
        int count = 0;
        var detail = new StringBuilder();
        for (int step = 1; step <= 15; step++)
        {
            long px = x + dx * step, py = y + dy * step, pz = z + dz * step;
            var pe = BlockAt(px, py, pz);
            string name = pe == null ? "<unloaded>" : pe.Name;
            bool air = pe == null || name == "minecraft:air" || name == "minecraft:cave_air" || name == "minecraft:void_air";
            if (!air) { count++; detail.Append(" [d=" + step + " (" + px + "," + py + "," + pz + ")=" + name + "]"); }
        }
        return count.ToString() + detail.ToString();
    }
}
'@

if (-not ('CrWorldInspect' -as [type])) {
	# 只引用核心程序集：解压缩在 PowerShell 侧完成，C# 只解析 NBT/方块调色板。
	Add-Type -TypeDefinition $code -Language CSharp
}

$regionDir = Join-Path $worldPath 'region'
$files = Get-ChildItem $regionDir -File -Filter 'r.*.*.mca'
$decompressed = 0
foreach ($file in $files) {
	$parts = $file.BaseName.Split('.')
	$rx = [int]$parts[1]
	$rz = [int]$parts[2]
	# 以共享读方式打开：游戏正开着也能读同一个区域文件。
	$stream = New-Object System.IO.FileStream($file.FullName, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
	$bytes = New-Object byte[] $stream.Length
	$readTotal = 0
	while ($readTotal -lt $bytes.Length) {
		$n = $stream.Read($bytes, $readTotal, $bytes.Length - $readTotal)
		if ($n -le 0) { break }
		$readTotal += $n
	}
	$stream.Dispose()
	for ($i = 0; $i -lt 1024; $i++) {
		$off = (($bytes[$i * 4] -band 0xFF) -shl 16) -bor (($bytes[$i * 4 + 1] -band 0xFF) -shl 8) -bor ($bytes[$i * 4 + 2] -band 0xFF)
		$sectors = $bytes[$i * 4 + 3] -band 0xFF
		if ($off -eq 0 -or $sectors -eq 0) { continue }
		$start = $off * 4096
		if ($start + 5 -ge $bytes.Length) { continue }
		$len = (($bytes[$start] -band 0xFF) -shl 24) -bor (($bytes[$start + 1] -band 0xFF) -shl 16) -bor (($bytes[$start + 2] -band 0xFF) -shl 8) -bor ($bytes[$start + 3] -band 0xFF)
		if ($len -le 1) { continue }
		$comp = $bytes[$start + 4] -band 0xFF
		$payload = New-Object byte[] ($len - 1)
		[System.Array]::Copy($bytes, $start + 5, $payload, 0, $len - 1)
		$raw = $null
		if ($comp -eq 2) {
			$input = New-Object System.IO.MemoryStream(,$payload)
			$z = New-Object System.IO.Compression.ZLibStream($input, [System.IO.Compression.CompressionMode]::Decompress)
			$out = New-Object System.IO.MemoryStream
			$z.CopyTo($out)
			$raw = $out.ToArray()
			$z.Dispose(); $out.Dispose(); $input.Dispose()
		} elseif ($comp -eq 1) {
			$input = New-Object System.IO.MemoryStream(,$payload)
			$g = New-Object System.IO.Compression.GZipStream($input, [System.IO.Compression.CompressionMode]::Decompress)
			$out = New-Object System.IO.MemoryStream
			$g.CopyTo($out)
			$raw = $out.ToArray()
			$g.Dispose(); $out.Dispose(); $input.Dispose()
		} else {
			continue
		}
		$cx = $rx * 32 + ($i % 32)
		$cz = $rz * 32 + [int][math]::Floor($i / 32)
		[CrWorldInspect]::RegisterChunk($cx, $cz, $raw)
		$decompressed++
	}
}

Write-Output "world: $worldPath"
Write-Output "region files: $($files.Count), chunks: $decompressed"
Write-Output ([CrWorldInspect]::ReportNamespace("circuit-redstone_logic:", 40))

