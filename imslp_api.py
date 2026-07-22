import asyncio
import sys
import os
import re
from urllib.parse import urlparse
import requests
from bs4 import BeautifulSoup
from playwright.async_api import async_playwright

# ---------- 配置 ----------
BASE_URL = "https://cn.imslp.org"
HEADLESS = False          # 设为 False 显示浏览器，便于处理验证码
TIMEOUT = 60000           # 60秒

# ---------- 1. 搜索 Bing（直接访问搜索 URL） ----------
async def search_imslp_via_bing(keyword, max_results=20):
    query = f"site:imslp.org {keyword}"
    search_url = f"https://www.bing.com/search?q={query}&mkt=zh-CN"
    print(f"搜索URL: {search_url}")

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=HEADLESS)
        context = await browser.new_context(
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            viewport={"width": 1280, "height": 800},
            locale="zh-CN"
        )
        page = await context.new_page()

        print("正在访问 Bing...")
        await page.goto(search_url, timeout=TIMEOUT)

        # 等待搜索结果出现
        try:
            await page.wait_for_selector('.b_algo', timeout=10000)
            print("搜索结果已加载。")
        except Exception:
            print("⚠️ 可能遇到验证码或页面加载失败。")
            # 截图保存以便调试
            await page.screenshot(path='bing_captcha.png')
            print("已保存截图 bing_captcha.png")
            # 检查页面内容中是否包含验证码关键词
            content = await page.content()
            if 'challenge' in content.lower() or 'verify' in content.lower():
                input("检测到验证码，请在浏览器中手动完成后按 Enter 继续...")
                # 等待结果加载
                try:
                    await page.wait_for_selector('.b_algo', timeout=15000)
                except:
                    print("仍未找到搜索结果，可能验证未通过或页面异常。")
                    await browser.close()
                    return []
            else:
                print("页面可能无法正常加载，请检查网络。")
                await browser.close()
                return []

        # 获取页面 HTML
        html = await page.content()
        await browser.close()

        # 解析 HTML
        soup = BeautifulSoup(html, 'html.parser')
        items = soup.find_all('li', class_='b_algo')
        if not items:
            items = soup.find_all('div', class_='b_algo')

        results = []
        for item in items:
            a = item.find('a', href=True)
            if not a:
                continue
            href = a['href']
            if 'imslp.org' in href and '/wiki/' in href:
                if '/Special:' in href or '/File:' in href or '/Category:' in href:
                    continue
                if href.startswith('/'):
                    href = 'https://' + urlparse(href).netloc + href if urlparse(href).netloc else 'https://imslp.org' + href
                title = a.get_text(strip=True)
                if title and not any(item['url'] == href for item in results):
                    results.append({'title': title, 'url': href})
                    if len(results) >= max_results:
                        break
        return results

# ---------- 2. 获取 PDF 链接 ----------
def get_pdf_links(page_url):
    session = requests.Session()
    session.headers.update({
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Cookie': 'imslpdisclaimeraccepted=yes'
    })
    try:
        resp = session.get(page_url, timeout=15)
        resp.raise_for_status()
        soup = BeautifulSoup(resp.text, 'html.parser')
    except Exception as e:
        print(f"获取页面失败: {e}")
        return []

    pdf_links = []
    for a in soup.find_all('a', href=True):
        href = a['href']
        if href.lower().endswith('.pdf'):
            if href.startswith('http'):
                full_url = href
            elif href.startswith('/'):
                full_url = f"https://{urlparse(page_url).netloc}{href}"
            else:
                continue
            filename = full_url.split('/')[-1]
            if not any(item['url'] == full_url for item in pdf_links):
                pdf_links.append({'name': filename, 'url': full_url})

    # 若没有直接 PDF，尝试从 File: 页面获取
    if not pdf_links:
        file_links = soup.find_all('a', href=re.compile(r'/wiki/File:'))
        for link in file_links:
            file_page_url = link['href']
            if file_page_url.startswith('/'):
                file_page_url = f"https://{urlparse(page_url).netloc}{file_page_url}"
            try:
                file_resp = session.get(file_page_url, timeout=10)
                if file_resp.status_code == 200:
                    file_soup = BeautifulSoup(file_resp.text, 'html.parser')
                    for a in file_soup.find_all('a', href=True):
                        if a['href'].lower().endswith('.pdf') and a['href'].startswith('http'):
                            pdf_url = a['href']
                            filename = pdf_url.split('/')[-1]
                            if not any(item['url'] == pdf_url for item in pdf_links):
                                pdf_links.append({'name': filename, 'url': pdf_url})
            except:
                pass
    return pdf_links

# ---------- 3. 下载 PDF ----------
def download_pdf(pdf_url, save_path):
    session = requests.Session()
    session.headers.update({
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Cookie': 'imslpdisclaimeraccepted=yes'
    })
    try:
        resp = session.get(pdf_url, stream=True, timeout=30)
        resp.raise_for_status()
        with open(save_path, 'wb') as f:
            for chunk in resp.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
        print(f"✅ PDF 已下载: {save_path}")
        return True
    except Exception as e:
        print(f"下载失败: {e}")
        return False

# ---------- 主程序 ----------
async def main():
    keyword = input("请输入要搜索的作曲家或作品名称: ").strip()
    if not keyword:
        print("搜索词不能为空")
        return

    print(f"\n正在通过 Playwright (Bing) 搜索 '{keyword}' ...")
    results = await search_imslp_via_bing(keyword, max_results=20)

    if not results:
        print("未找到任何结果。")
        return

    print(f"\n找到 {len(results)} 个匹配结果：")
    for idx, item in enumerate(results, start=1):
        print(f"{idx}. {item['title']}")
        print(f"   {item['url']}")

    while True:
        try:
            choice = int(input(f"\n请输入要查看的作品编号 (1-{len(results)})，输入0退出: "))
            if choice == 0:
                return
            if 1 <= choice <= len(results):
                break
            else:
                print(f"请输入1到{len(results)}之间的数字。")
        except ValueError:
            print("请输入有效的数字。")

    selected = results[choice - 1]
    print(f"\n正在获取《{selected['title']}》的PDF文件...")
    pdfs = get_pdf_links(selected['url'])

    if not pdfs:
        print("该作品暂无PDF文件。")
        return

    print(f"\n找到 {len(pdfs)} 个PDF文件：")
    for idx, pdf in enumerate(pdfs, start=1):
        print(f"{idx}. {pdf['name']}")

    while True:
        try:
            pdf_choice = int(input("\n请输入要下载的PDF编号 (输入0退出): "))
            if pdf_choice == 0:
                return
            if 1 <= pdf_choice <= len(pdfs):
                break
            else:
                print(f"请输入1到{len(pdfs)}之间的数字。")
        except ValueError:
            print("请输入有效的数字。")

    selected_pdf = pdfs[pdf_choice - 1]
    safe_title = re.sub(r'[\\/*?:"<>|]', '_', selected['title'])
    filename = f"{safe_title}_{selected_pdf['name']}"
    save_path = os.path.join(os.getcwd(), filename)

    print(f"\n开始下载: {filename}")
    download_pdf(selected_pdf['url'], save_path)
    print("所有操作完成！")

if __name__ == "__main__":
    try:
        import playwright
    except ImportError:
        print("请先安装 Playwright: pip install playwright && playwright install chromium")
        sys.exit(1)
    asyncio.run(main())