import os

dir = os.getcwd()
print(dir)

for file in os.listdir(dir):
    if file.find('.gitignore') > 0:
        print(file)
        os.rename(os.path.join(dir,file),os.path.join(dir,'.gitignore'))
    
