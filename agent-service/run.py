"""启动入口：python run.py  等价于  uvicorn app.main:app --host 0.0.0.0 --port 8800"""
import uvicorn

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=8800, reload=False)
